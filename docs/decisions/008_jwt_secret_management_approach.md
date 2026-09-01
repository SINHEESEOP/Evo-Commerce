### 문제 상황
소스에 하드코딩됐던 JWT 서명 키를 걷어내면서, 로컬 개발 편의와 시크릿 관리 안전성 사이에서 어떤 방식으로 시크릿을 공급할지 판단이 필요했다.

### 검토한 대안
- `application.yaml`에 로컬용 폴백 기본값을 남겨두기
- 로컬 기본값만 `application-local.yaml`(git 추적 제외)로 분리
- 프로퍼티가 하나뿐인 지금 시점에 별도 `@ConfigurationProperties` 클래스(`JwtProperties`) 도입
- Vault/AWS Secrets Manager 같은 외부 시크릿 관리 서비스 연동
- 시크릿은 예외 없이 항상 환경 변수(`JWT_SECRET`)로만 공급, 기본값 없음

### 결정 및 이유
마지막 방식을 채택했다. 폴백 기본값은 그 값이 그대로 운영에 새어나갈 위험이 있고, `application-local.yaml` 분리는 이미 `datasource` 같은 다른 로컬 값이 프로필 구분 없이 공통 블록에 있는 기존 컨벤션과 충돌해(같은 "로컬 값"인데 프로퍼티마다 관리 위치가 달라짐) 되돌렸다. `@ConfigurationProperties` 클래스와 외부 시크릿 관리 서비스는 프로퍼티가 하나뿐인 현재 규모와 배포 환경(Docker Compose 기반 로컬/단일 서버)에 비해 과한 설계라 채택하지 않았다.

### 적용 결과
`JWT_SECRET`이 없으면 로컬이든 `prod`든 애플리케이션 기동 자체가 실패한다(`PlaceholderResolutionException`). 두 경우 모두 재현해 확인했다. `@Value`는 필드가 아니라 생성자 파라미터로 받았다 — 필드 주입은 생성자 실행 이후에 일어나서 필드 초기화 블록에서 곧바로 참조하면 `NullPointerException`이 나기 때문이다.

### 관련 이슈
[ISSUE-06](../../issues/ISSUE-06_jwt_secret_hardcoded_in_source.md)
