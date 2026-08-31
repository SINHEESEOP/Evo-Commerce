# [Bug] 주문 결제와 재고 차감이 응답에는 반영되지만 DB에는 저장되지 않음

### 증상
- `POST /api/orders`(`OrderFacade.placeOrder`)를 호출하면 응답 바디의 `status`는 `PAID`로 내려온다.
- 하지만 같은 주문을 다시 조회하면 `status`가 `CREATED`로 남아 있다.
- 주문에 담았던 상품의 재고도 요청 수량만큼 줄어들지 않고 원래 값 그대로 남아 있다.
- 어떤 예외도 발생하지 않고, 응답은 200 OK다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/OrderFacade.java`
- `src/main/java/com/evo/commerce/domain/product/Product.java`
- `src/main/java/com/evo/commerce/domain/order/Order.java`

### 재현 절차
1. `@SpringBootTest`(트랜잭션으로 감싸지 않은 순수 통합 테스트) 환경에서 아래 순서로 재현한다.
   ```java
   User user = userRepository.save(User.builder()...build());
   Product product = productRepository.save(Product.builder().stock(10)...build());

   OrderCreateRequest request = new OrderCreateRequest(
           List.of(new OrderItemRequest(product.getId(), 2)));

   OrderResponse response = orderFacade.placeOrder(user.getId(), request);
   System.out.println(response.status());              // PAID

   Order dbOrder = orderRepository.findById(response.id()).orElseThrow();
   Product dbProduct = productRepository.findById(product.getId()).orElseThrow();
   System.out.println(dbOrder.getStatus());             // CREATED
   System.out.println(dbProduct.getStock());            // 10 (그대로)
   ```
2. `show-sql: true`로 Hibernate 로그를 함께 보면, `users`/`products`/`orders`/`order_items` INSERT는 나가지만 `products` 테이블에 대한 UPDATE, `orders` 테이블의 `status` 변경을 위한 UPDATE는 한 번도 나가지 않는다.

### 관찰
- `OrderFacade.placeOrder()`에는 `@Transactional`이 없다.
- `userRepository.findById()`, `productRepository.findById()`는 각각 자기 자신의 트랜잭션 안에서 실행되고 끝나면 반환한다. 그 시점에 반환된 `User`/`Product`는 이미 준영속 상태다.
- `product.decreaseStock(quantity)`는 이 준영속 `Product` 객체의 필드를 자바 메모리 상에서만 바꾼다. 이 변경을 추적하고 flush할 영속성 컨텍스트가 이미 없으므로, 어떤 SQL도 발생하지 않고 조용히 사라진다.
- `orderRepository.save(order)`는 명시적으로 호출했기 때문에 `Order`/`OrderItem`은 정상적으로 INSERT된다. 하지만 `save()`가 반환한 시점에도 그 트랜잭션은 이미 끝났으므로, 반환된 `saved` 역시 준영속 상태다.
- 바로 다음 줄의 `saved.pay()`는 이 준영속 `Order`의 `status` 필드만 메모리에서 바꿀 뿐, 역시 어떤 UPDATE도 만들어내지 않는다.
- 결과적으로 `OrderMapper.toResponse(saved)`가 응답으로 내려주는 값은 메모리 상에서는 맞지만 DB 상태와는 무관한 값이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성)

### 해결 방안
(해결 시 작성)
