# [Bug] 표현/응용/도메인 폴더 분리가 실제 의존 방향을 강제하지 못함

### 증상
- `domain/{order,product,user,notification}` 각각이 `presentation`/`application`/`domain` 하위 패키지로 나뉘었지만, 모든 클래스가 `public`으로 선언돼 있어 다른 도메인의 임의 계층을 아무 제약 없이 import할 수 있다.
- 예를 들어 `com.evo.commerce.domain.product.presentation.ProductController`에서 `com.evo.commerce.domain.order.domain.OrderRepository`를 직접 import해서 사용해도 컴파일 에러나 경고가 전혀 발생하지 않는다.
- `domain` 하위 패키지 안에 순수 도메인 모델(`Order`, `Product`, `User` 등)과 영속성 포트(`OrderRepository` 등 Spring Data JPA 인터페이스), 외부 API 어댑터(`TossPaymentClient`)가 구분 없이 섞여 있다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/`, `domain/product/`, `domain/user/`, `domain/notification/` 전체 하위 패키지 구조
- `docs/wiki/Phase2_기획및설계.md` "패키지 구조 방향" 절 (이 재정비를 하게 된 원래 배경)

### 재현 절차
1. `com.evo.commerce.domain.product.presentation.ProductController`에 임의로 `import com.evo.commerce.domain.order.domain.OrderRepository;`를 추가하고 필드로 주입받는 코드를 작성한다.
2. `./gradlew compileJava`를 실행한다.
3. 아무 에러 없이 컴파일이 성공한다 — 표현 계층이 다른 도메인의 영속성 계층을 직접 참조하는, 설계 문서가 막고자 했던 바로 그 상황이 컴파일러 수준에서 전혀 저지되지 않는다.

### 관찰
- Java의 패키지는 이름 규칙일 뿐 물리적인 접근 차단 장치가 아니다. `public` 클래스는 어떤 패키지에서든 자유롭게 import할 수 있으므로, 폴더를 `presentation`/`application`/`domain`으로 나눈 것만으로는 "어떤 계층이 어떤 계층에 의존해도 되는가"를 강제할 수 없다.
- 실제로 의존 방향을 강제하려면 package-private 접근 제한자, 자바 모듈 시스템(JPMS), 또는 ArchUnit 같은 아키텍처 테스트 도구 중 하나가 추가로 필요한데, 이번 재정비에서는 그중 아무것도 도입하지 않았다.
- `TossPaymentClient`(외부 HTTP 클라이언트)와 `OrderRepository`(JPA 인터페이스)가 `Order`, `OrderStatus` 같은 순수 도메인 모델과 같은 `domain` 패키지에 놓여 있어, "도메인 계층"이라는 이름이 실제로는 성격이 다른 것들을 뭉뚱그리고 있다.

### 상태
`[CLOSED]`

### 원인 분석
Java의 패키지는 이름 규칙일 뿐 물리적인 접근 차단 장치가 아니다. `presentation`/`application`/`domain`이라는 폴더 이름은 개발자 간의 관례(convention)이지 컴파일러가 검사하는 제약(constraint)이 아니므로, `public` 클래스는 어떤 패키지에서든 자유롭게 import된다. 폴더 재배치만으로는 원래 설계 문서(`docs/wiki/Phase2_기획및설계.md`)가 의도했던 "의존 방향 강제"를 달성할 수 없었다.

### 해결 방안
`archunit-junit5`를 테스트 의존성으로 추가하고 `src/test/java/com/evo/commerce/architecture/PackageArchitectureTest.java`에 두 규칙을 적용했다: (1) 도메인 계층은 응용/표현 계층에 의존할 수 없다, (2) 표현 계층은 도메인 계층을 직접 참조할 수 없고 반드시 응용 계층을 거쳐야 한다. 테스트 클래스는 `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`로 검사 대상에서 제외해, `@WebMvcTest`가 DTO 스텁 생성을 위해 도메인 enum을 참조하는 정상적인 테스트 코드까지 위반으로 잡히는 오탐을 막았다.

또한 `TossPaymentClient`(order), `SseEmitterRegistry`(notification)처럼 외부 시스템(Toss API, 살아있는 SSE 연결)과 맞닿는 어댑터 클래스를 새로 만든 `infrastructure` 패키지로 분리해, 순수 도메인 모델과 인프라 어댑터가 같은 `domain` 패키지에 섞이는 문제를 해결했다. package-private 접근 제한자는 "같은 도메인 내 계층 간 접근은 허용, 다른 도메인의 표현 계층에서의 접근은 금지"처럼 세밀한 규칙을 표현할 수 없어 기각했고, 자바 모듈 시스템(JPMS)은 도메인 4개 규모에 비해 설정 비용이 과하다고 판단해 기각했다.
