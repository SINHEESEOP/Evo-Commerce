### 문제 상황
springdoc은 `/v3/api-docs`, `/swagger-ui/index.html`을 별도 설정 없이 모든 환경에서 기본적으로 활성화한다. 이 프로젝트에는 `dev`/`prod` 프로필 분리가 아예 없어서, 배포 환경에서만 이 문서를 차단할 지점 자체가 없었다.

### 검토한 대안
- `SwaggerConfig` 빈에 `@Profile("!prod")`를 걸어 빈 등록 자체를 차단
- `application.yaml`에 프로필 문서를 추가하고 springdoc의 `enabled` 프로퍼티로 제어

### 결정 및 이유
후자를 선택했다. springdoc이 이미 `springdoc.api-docs.enabled`/`springdoc.swagger-ui.enabled`라는 표준 스위치를 제공하므로, 자바 코드에 조건을 하드코딩하지 않고 설정 파일만으로 환경별 차이를 추적할 수 있다. 두 프로퍼티는 각각 문서 원본(JSON)과 UI 화면을 막는 대상이 다르므로 반드시 함께 꺼야 한다.

### 적용 결과
`application.yaml`에 `spring.config.activate.on-profile: prod` 문서를 추가하고 그 안에서 두 프로퍼티를 `false`로 설정했다. `prod` 프로필로 기동하면 두 엔드포인트 모두 라우팅되지 않고, 프로필을 지정하지 않은 기본(dev) 상태는 기존과 동일하게 동작한다.

### 관련 이슈
[ISSUE-04](../../issues/ISSUE-04_swagger_ui_exposed_without_environment_restriction.md)
