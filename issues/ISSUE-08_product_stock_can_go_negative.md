# [Bug] 재고보다 많은 수량으로 차감하면 재고가 음수가 됨

### 증상
- `Product.decreaseStock(int quantity)`를 재고 수량보다 큰 `quantity`로 호출하면 예외 없이 정상 처리되고, `stock` 필드가 음수 값으로 저장된다.

### 환경
- `src/main/java/com/evo/commerce/domain/product/Product.java`
- `src/test/java/com/evo/commerce/domain/product/ProductTest.java`

### 재현 절차
1. 순수 자바 단위 테스트로 재현 가능하다(스프링 컨텍스트/DB 불필요).
   ```java
   Product product = Product.builder()
           .name("테스트 상품")
           .price(10000)
           .stock(3)
           .build();

   product.decreaseStock(10);

   System.out.println(product.getStock()); // -7
   ```
2. `stock`이 `-7`로 찍히는데도 어떤 예외도 발생하지 않는다.

### 관찰
- `decreaseStock()`은 파라미터로 받은 `quantity`를 검증 없이 그대로 `this.stock`에서 빼기만 한다.
- 재고 차감 로직을 `Product` 엔티티 내부 메서드로 옮겨놓긴 했지만, 정작 "요청 수량이 현재 재고보다 많으면 안 된다"는 핵심 불변식(invariant) 체크가 빠져 있다.
- 이 메서드를 호출하는 서비스 계층 코드가 없는 현재 시점에도, 엔티티 자체의 단위 테스트만으로 결함이 드러난다 — 즉 동시성(여러 요청이 동시에 들어오는 상황)과는 무관하게, 단일 요청 흐름에서도 재현되는 로직 결함이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
