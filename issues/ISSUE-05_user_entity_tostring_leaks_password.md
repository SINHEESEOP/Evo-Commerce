# [Bug] User 엔티티의 자동 생성 toString()이 평문 비밀번호를 로그에 남김

### 증상
- 회원 저장 흐름을 확인하려고 저장 직후 엔티티를 로그로 찍었더니, 비밀번호 원문이 그대로 로그에 출력된다.
  ```
  INFO ... UserRepositoryTest : 회원가입 저장 완료: User(id=2, email=tester@evo-commerce.com, password=plain1234!, name=테스터, role=USER, createdAt=null, updatedAt=null)
  ```
- `log.info("{}", user)`처럼 엔티티 객체 하나를 그대로 로그에 넘기는 코드가 앞으로 서비스/컨트롤러 계층에 계속 추가될 텐데, 그때마다 동일하게 비밀번호가 노출된다.

### 환경
- `src/main/java/com/evo/commerce/domain/user/User.java` — `@Data` (Lombok)
- Spring Boot 3.5.3, Logback 기본 설정

### 재현 절차
1. `docker compose up -d` 로 MySQL을 띄운다.
2. 아래 테스트를 실행한다.
   ```bash
   ./gradlew test --tests "com.evo.commerce.domain.user.UserRepositoryTest"
   ```
3. 테스트 로그(`build/test-results/test/TEST-com.evo.commerce.domain.user.UserRepositoryTest.xml`)에서 `회원가입 저장 완료` 라인을 확인한다. `password=` 뒤에 평문 비밀번호가 그대로 찍혀 있다.

### 관찰
- `@Data`는 클래스에 선언된 모든 필드를 대상으로 `toString()`을 생성한다. `password` 필드를 제외할 방법을 따로 지정하지 않으면 자동으로 포함된다.
- 지금은 테스트 코드의 디버그성 로그 한 줄이지만, 같은 습관(엔티티를 통째로 로그/예외 메시지에 넘기는 것)이 서비스 계층에서 반복되면 운영 로그 수집기(ELK, Datadog 등)에 사용자 비밀번호가 그대로 적재된다.

### 상태
`[OPEN]`

### 원인 분석
(해결 후 작성)

### 해결 방안
(해결 후 작성)
