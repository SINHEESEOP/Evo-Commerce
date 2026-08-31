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
`[CLOSED]`

### 원인 분석
`OrderFacade.placeOrder()`에 트랜잭션 경계(`@Transactional`)가 없었다. `JpaRepository`의 `findById()`/`save()`는 각각 자기 자신의 트랜잭션을 갖고, 그 메서드가 반환되는 순간 트랜잭션과 영속성 컨텍스트가 함께 끝난다. 그래서 `findById()`로 가져온 `Product`, `save()`가 반환한 `Order`는 반환 즉시 준영속 상태가 됐고, 이후 `product.decreaseStock()`과 `saved.pay()`로 만든 필드 변경은 그 변경을 추적할 영속성 컨텍스트가 이미 없어 어떤 UPDATE SQL도 만들어내지 못한 채 사라졌다. `Order`/`OrderItem`은 `save()` 호출 자체가 명시적인 INSERT였기 때문에 정상 저장됐지만, 그 이후의 상태 변경(`pay()`)은 저장되지 않았다.

이 결함은 Mockito로 리포지토리를 모킹한 `OrderFacadeTest`로는 드러나지 않았다. Mock은 트랜잭션·영속성 컨텍스트 개념 자체가 없어서, 테스트 코드가 들고 있는 객체의 필드 변경이 그대로 보였을 뿐이다. 실제 DB로 검증하는 `@SpringBootTest`(테스트 메서드 자체를 트랜잭션으로 감싸지 않은 형태)로 응답 직후 DB를 다시 조회했을 때만 재현됐다.

### 해결 방안
`OrderFacade.placeOrder()`에 `@Transactional`을 추가해, `findById()`로 가져온 엔티티들과 `save()`가 반환한 `Order`가 메서드 종료(트랜잭션 커밋) 시점까지 계속 영속 상태를 유지하도록 했다. 이제 `decreaseStock()`/`pay()`가 만든 변경은 커밋 시점에 더티 체킹으로 자동 반영된다. 부수 효과로, 여러 아이템 중 하나가 재고 부족으로 실패하는 경우 앞서 처리된 아이템의 재고 차감도 함께 롤백된다(`BusinessException`이 `RuntimeException`이라 Spring 기본 롤백 정책에 그대로 들어맞는다).

회귀 테스트로 `OrderFacadeTransactionTest`를 추가했다. 트랜잭션으로 감싸지 않은 `@SpringBootTest`에서 `placeOrder()`를 호출한 뒤 같은 리포지토리로 DB를 다시 조회해, 응답 값이 아니라 실제 저장된 상태(`status = PAID`, 차감된 `stock`)를 검증한다. Mock 기반 테스트와 달리 이 구성이어야 트랜잭션 누락을 실제로 잡아낸다는 것도 함께 확인했다. 트랜잭션 경계와 Mock 테스트의 한계에 대한 상세 논의는 `docs/study/Facade_패턴과_주문_오케스트레이션.md` 참고.
