# [Bug] 주문에 담은 상품이 저장 후 다시 조회하면 사라짐

### 증상
- `Order.addItem(orderItem)`으로 주문에 상품을 담고 `orderRepository.save(order)`를 호출하면 예외 없이 저장된다.
- 하지만 영속성 컨텍스트를 비운 뒤 같은 주문을 다시 조회하면 `order.getOrderItems()`가 빈 리스트로 나온다.
- DB의 `order_items` 테이블을 직접 조회하면 방금 저장된 행의 `order_id` 컬럼이 `NULL`로 찍혀 있다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/Order.java`
- `src/main/java/com/evo/commerce/domain/order/OrderItem.java`

### 재현 절차
1. `@SpringBootTest` + `@Transactional` 환경에서 아래 순서로 재현한다.
   ```java
   Order order = Order.builder().user(user).build();
   OrderItem item = OrderItem.builder().product(product).quantity(2).build();
   order.addItem(item);

   Order saved = orderRepository.save(order);
   em.flush();
   em.clear();

   Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
   System.out.println(reloaded.getOrderItems().size()); // 0
   ```
2. `SELECT order_id FROM order_items;`로 직접 조회하면 값이 `NULL`로 저장돼 있다.

### 관찰
- `@OneToMany(mappedBy = "order")`로 선언된 `Order.orderItems`는 연관관계의 주인이 아니다. 실제 FK 컬럼(`order_id`)은 `OrderItem.order`(`@ManyToOne` + `@JoinColumn(name = "order_id")`)가 주인이다.
- `Order.addItem()`은 `this.orderItems.add(orderItem)`만 수행하고, `orderItem`의 `order` 필드는 어디에서도 설정하지 않는다.
- JPA는 주인이 아닌 쪽(`Order.orderItems`)의 컬렉션 상태를 FK 저장에 반영하지 않는다. `cascade = ALL`로 `OrderItem`이 함께 INSERT되긴 하지만, `order` 필드가 `null`인 채로 INSERT되므로 `order_id` 컬럼도 `NULL`로 저장된다.
- 같은 영속성 컨텍스트 안에서 저장 직후 `order.getOrderItems()`를 읽으면 메모리에 들어있는 리스트가 그대로 보이므로 문제가 드러나지 않는다. 영속성 컨텍스트를 비우고 DB에서 다시 읽어와야 비로소 드러난다.

### 상태
`[CLOSED]`

### 원인 분석
`Order.orderItems`는 `mappedBy = "order"`로 선언된 연관관계의 주인이 아닌 쪽이라, JPA가 flush 시점에 이 컬렉션의 상태를 FK 컬럼 값 결정에 사용하지 않는다. 실제 FK(`order_id`)는 연관관계의 주인인 `OrderItem.order` 필드가 결정하는데, `Order.addItem()`이 그 필드를 설정하지 않아 항상 `null`로 남았고, 그 결과 `order_items` 행이 `order_id = NULL`로 저장됐다.

### 해결 방안
`Order.addItem()`이 컬렉션에 추가하기 전에 `orderItem.assignOrder(this)`를 호출해 연관관계의 주인 쪽 필드도 함께 설정하도록 수정했다(연관관계 편의 메서드). 이 메서드는 애그리거트 루트인 `Order`에만 진입점을 두고, `OrderItem.assignOrder()`는 패키지 전용으로 좁혀서 `Order`를 거치지 않고는 이 관계를 맺을 수 없도록 했다. `em.flush()` + `em.clear()` 이후 다시 조회해도 주문 아이템이 유지되는지 검증하는 회귀 테스트를 `OrderRepositoryTest`에 추가했다. 상세 트레이드오프는 `docs/retrospectives/Step_2.2_리뷰.md` 참고.
