# [Bug] User 엔티티에 붙인 @Data가 비밀번호 로그 노출과 컬렉션 유실을 동시에 유발

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
- 같은 `@Data`가 만드는 `equals()`/`hashCode()`도 별개의 증상을 낸다. `id`는 `IDENTITY` 전략으로 저장 시점에 채번되므로, 저장 전 `HashSet`에 담아둔 엔티티를 저장 후 같은 Set에서 `contains()`로 찾으면 `false`가 나온다. 아래처럼 재현된다.
  ```
  저장 전 hashCode: -277284016
  저장 후 hashCode: -345621647
  저장 전 contains(): true
  저장 후 contains(): false
  순회로 찾아지는가: true
  ```
  객체가 사라진 게 아니라(순회하면 여전히 존재), `HashSet` 내부 버킷 위치와 저장 후 다시 계산한 `hashCode`가 어긋나 찾지 못하는 것이다.

### 상태
`[CLOSED]`

### 원인 분석
두 증상 모두 `User` 엔티티에 붙인 `@Data` 하나에서 비롯됐다. `@Data`는 `@ToString`과 `@EqualsAndHashCode`를 포함한 5개 기능을 한 번에 붙이는데, `@ToString`은 `password`를 포함한 모든 필드를 그대로 문자열로 노출하고, `@EqualsAndHashCode`는 DB가 채번하는 `id`를 포함한 모든 필드로 `hashCode`를 계산한다. `id`처럼 생명주기 중 값이 바뀌는 필드가 해시 계산에 들어가면, 저장 전/후로 같은 객체의 `hashCode`가 달라져 `HashSet`/`HashMap`이 그 객체를 잃어버린다.

### 해결 방안
- `@Data`를 걷어내고 `@Getter`만 남겨, 값 변경은 명시적 메서드(현재는 `@Builder` 생성자)로만 가능하게 했다.
- `toString()`을 직접 재정의해 `id`/`email`/`name`/`role`만 노출하고 `password`는 제외했다.
- `equals()`는 `id`가 둘 다 `null`이 아니고 같을 때만 `true`, `hashCode()`는 `getClass().hashCode()`로 클래스 단위 상수를 반환하도록 재정의했다. 상수 해시값을 쓰면 저장 전후로 값이 절대 바뀌지 않아 `HashSet`이 항상 같은 버킷을 찾고, 그 안에서는 참조 동일성(`this == o`)으로 저장 전 객체를, `id` 비교로 저장 후 서로 다른 인스턴스를 구분한다. (트레이드오프: 모든 `User` 인스턴스가 같은 해시값을 공유해 `HashSet` 내부 분산은 나빠지지만, 이 프로젝트 규모에서는 정확성이 성능보다 우선이다.)
- 리팩터링 후 동일한 재현 시나리오를 회귀 테스트(`UserRepositoryTest#저장_전에_컬렉션에_담아둔_사용자를_저장_후에도_찾을_수_있다`)로 추가해 `contains()`가 `true`로 돌아오는 것을 확인했고, 저장 로그(`회원가입 저장 완료: User(id=10, email=..., name=..., role=USER)`)에 `password`가 더는 찍히지 않는 것도 확인했다.
