# [Bug] OrderFacade가 추상화가 아닌 TossPaymentClient 구체 클래스에 직접 의존함

### 증상
- `OrderFacade`(응용 계층)의 필드 중 `OrderRepository`, `ProductRepository`, `UserRepository`는 모두 인터페이스라서 Spring Data JPA가 구현체를 자동 생성해주는 추상화에만 의존한다.
- 반면 `TossPaymentClient`는 인터페이스가 아니라 `public class`로 선언된 구체 클래스다. `OrderFacade`가 이 클래스를 직접 `import`해서 필드로 갖고 있어, 결제 승인을 "Toss API를 `RestClient`로, 특정 인증 헤더 형식으로 호출하는" 구체적인 방법까지 알고 있는 상태다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/application/OrderFacade.java`
- `src/main/java/com/evo/commerce/domain/order/infrastructure/TossPaymentClient.java`

### 재현 절차
1. `OrderFacade`의 필드 선언을 확인한다.
   ```java
   private final OrderRepository orderRepository;      // 인터페이스
   private final ProductRepository productRepository;  // 인터페이스
   private final UserRepository userRepository;        // 인터페이스
   private final TossPaymentClient tossPaymentClient;   // 구체 클래스 — 나머지와 성격이 다르다
   ```
2. `TossPaymentClient` 선언을 확인한다 — `interface`가 아니라 `@Component public class`다.
3. 결제 승인 방식을 Toss 외 다른 PG사로 바꾸거나, 여러 PG사를 동시에 지원해야 하는 상황을 가정하면 `OrderFacade` 코드 자체를 고쳐야 한다는 걸 확인할 수 있다 — 추상화를 거치지 않고 구체 클래스에 직접 묶여 있기 때문이다.

### 관찰
- Step 2.8에서 `PackageArchitectureTest`(ArchUnit)에 추가한 두 규칙은 이 문제를 검사 범위에 포함하지 않는다 — "표현 계층이 도메인 계층을 직접 참조하는지"만 검사했지, "응용 계층이 인프라의 구체 클래스에 직접 의존하는지"는 다른 축의 문제라 규칙에 없다.
- 지금 이걸 인터페이스로 분리하는 게 실익이 있는지 따져보면: (1) Toss 외 다른 PG사를 추가할 계획이 로드맵(`docs/milestones/Phase3.md`~`Phase5.md`)에 없고, (2) `OrderFacadeTest`가 이미 `@Mock TossPaymentClient`로 문제없이 목킹되고 있어 테스트도 막혀 있지 않다. 즉 DIP가 실전에서 값어치를 하는 두 가지 전형적인 이유(구현체 교체 필요, 테스트가 구체 클래스라서 막힘) 중 어느 것도 지금은 해당하지 않는다.
- 그래서 지금 시점에 `PaymentGateway` 같은 인터페이스를 도입하는 건 YAGNI(You Aren't Gonna Need It) 관점에서 과한 추상화로 판단해 고치지 않고 이슈로만 남긴다. 실제로 두 번째 결제 수단이 필요해지거나, 결제 흐름 테스트에서 구체 클래스 목킹이 실질적으로 막히는 상황이 생기면 그때 재검토한다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정 — 방향성만 메모: `order.application` 또는 `order.domain`에 `PaymentGateway` 인터페이스를 만들고 `confirm(...)`을 정의, `TossPaymentClient`는 `order.infrastructure`에 남아 이를 `implements`, `OrderFacade` 필드 타입을 `PaymentGateway`로 변경)
