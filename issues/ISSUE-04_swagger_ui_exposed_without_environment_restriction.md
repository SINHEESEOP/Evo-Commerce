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
`[OPEN]`

### 원인 분석
(해결 후 작성)

### 해결 방안
(해결 후 작성)
