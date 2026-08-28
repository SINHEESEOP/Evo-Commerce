# [Bug] Swagger UI와 OpenAPI 문서가 환경 구분 없이 항상 노출됨

### 증상
- 애플리케이션을 기동하면 별도 설정 없이 `/swagger-ui/index.html`, `/v3/api-docs`가 즉시 200으로 응답한다.
- 기동 로그에 다음 경고가 매번 찍힌다.
  ```
  WARN ... SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production, set the property 'springdoc.api-docs.enabled=false'
  WARN ... SpringDoc /swagger-ui.html endpoint is enabled by default. To disable it in production, set the property 'springdoc.swagger-ui.enabled=false'
  ```
- 현재는 로컬 개발 환경만 존재해 문제가 드러나지 않지만, 이 상태로 배포되면 운영 환경에서도 동일한 경로로 API 문서가 그대로 열린다.

### 환경
- Spring Boot 3.5.3
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`
- `src/main/java/com/evo/commerce/global/config/SwaggerConfig.java`
- `src/main/resources/application.yaml` (프로필 분리 없이 단일 설정 파일만 존재)

### 재현 절차
1. `./gradlew bootJar` 로 빌드 후 `docker compose up -d --build` 로 기동한다.
2. 기동 로그에서 `SpringDocAppInitializer`의 두 WARN 라인을 확인한다.
3. 아래 요청으로 응답 코드를 확인한다.
   ```bash
   curl -o /dev/null -s -w "%{http_code}\n" http://localhost:8080/v3/api-docs
   curl -o /dev/null -s -w "%{http_code}\n" http://localhost:8080/swagger-ui/index.html
   ```
   두 요청 모두 200이 반환된다.

### 관찰
- `SwaggerConfig`는 `OpenAPI` 빈 하나만 등록할 뿐, `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` 같은 노출 제어 프로퍼티를 전혀 설정하지 않는다.
- 프로젝트에 아직 `dev`/`prod` 같은 스프링 프로필 분리 자체가 없어서, "로컬에서만 켜고 배포 환경에서는 끈다"는 구분을 걸 지점이 없다.

### 상태
`[CLOSED]`

### 원인 분석
springdoc은 `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled` 프로퍼티를 별도로 지정하지 않으면 두 값을 기본적으로 `true`로 두고 모든 환경에서 동일하게 동작한다. 이 프로젝트에는 애초에 `dev`/`prod` 같은 스프링 프로필 분리 자체가 없었기 때문에, "로컬에서는 켜고 배포 환경에서는 끈다"는 조건을 걸 지점이 존재하지 않았다. 즉 문제의 본질은 Swagger 설정 하나가 아니라, 환경별로 값을 달리 가져갈 구조가 아직 없었다는 데 있었다.

### 해결 방안
- 대안 1: `SwaggerConfig` 빈에 `@Profile("!prod")`를 걸어 빈 등록 자체를 막는다 — 동작은 하지만, springdoc이 이미 `enabled` 프로퍼티라는 표준 스위치를 제공하는 상황에서 굳이 자바 코드에 조건을 하드코딩하는 것은 설정 파일만으로 환경별 차이를 추적할 수 없게 만든다.
- 대안 2 (채택): `application.yaml`에 `spring.config.activate.on-profile: prod` 문서를 `---`로 추가하고, 그 블록 안에서 `springdoc.api-docs.enabled: false`와 `springdoc.swagger-ui.enabled: false`를 함께 설정한다. `swagger-ui`만 끄면 `/v3/api-docs` JSON 원본은 여전히 노출되므로, 문서 생성 자체를 막는 `api-docs` 쪽도 반드시 함께 꺼야 한다.
- 검증: `-Dspring.profiles.active=prod`로 기동했을 때 `/v3/api-docs`, `/swagger-ui/index.html` 모두 `NoResourceFoundException`으로 라우팅되어(더 이상 컨트롤러가 등록되지 않음) 차단이 확인됐고, 프로필을 지정하지 않은 기본(dev) 상태에서는 기존과 동일하게 200이 반환되는 것을 확인했다.
