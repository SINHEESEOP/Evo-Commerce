### 문제 상황
`Order`-`OrderItem` 양방향 연관관계에서 FK가 유실되는 결함을 고치면서, 연관관계를 맺는 진입점을 얼마나 좁게 둘지 판단이 필요했다.

### 검토한 대안
- `OrderItem.assignOrder()`를 `public`으로 열어 `OrderItem` 쪽에서도 직접 호출 가능하게 함
- `assignOrder()`를 패키지 전용으로 좁히고 `Order.addItem()`을 유일한 진입점으로 유지

### 결정 및 이유
후자를 채택했다. `OrderItem`은 독립된 `Repository`가 없는 `Order`의 하위 엔티티(애그리거트 루트가 아님)라, 외부 코드가 `Order`를 거치지 않고 이 관계를 맺을 수 있는 길을 열어두고 싶지 않았다.

### 적용 결과
`Order.addItem()`이 컬렉션에 추가하기 전에 `orderItem.assignOrder(this)`를 호출해 연관관계의 주인 쪽 필드까지 함께 설정한다. `em.flush()` + `em.clear()` 이후 다시 조회해도 주문 아이템이 유지되는지 검증하는 회귀 테스트를 추가했다.

### 관련 이슈
[ISSUE-10](../../issues/ISSUE-10_order_item_order_id_saved_as_null.md)
