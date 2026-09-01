### 문제 상황
`Product.decreaseStock()`에 재고 검증을 추가하면서, 수량이 0 이하인 경우와 재고보다 많은 수량을 요청한 경우를 같은 예외로 통일할지, 가격 타입을 무엇으로 둘지 판단이 필요했다.

### 검토한 대안
- `quantity <= 0` 검증도 `BusinessException`(비즈니스 규칙 위반)으로 통일
- `price` 필드를 `BigDecimal`로 변경

### 결정 및 이유
`quantity <= 0`은 `IllegalArgumentException`(메서드 계약 위반)으로, 재고 부족은 `BusinessException`(비즈니스 규칙 위반)으로 구분했다. 전자는 정상적인 API 요청 흐름에서는 DTO 검증 단계에서 이미 걸러질 값이라, 굳이 HTTP 응답 매핑까지 필요한 비즈니스 예외로 다룰 이유가 없다고 판단했다. `price`는 `BigDecimal`로 바꾸지 않았다 — KRW은 소수점 단위가 없어 정밀 소수 연산이 필요하지 않고, 단일 상품 단가는 `int` 범위로 충분해 지금 단계의 과설계로 판단했다.

### 적용 결과
`decreaseStock()`은 계약 위반(`IllegalArgumentException`)과 비즈니스 규칙 위반(`BusinessException` + `ProductErrorCode.INSUFFICIENT_STOCK`)을 구분해서 던진다. 여러 상품 금액을 합산하는 지점(주문 총액 등)에서 오버플로우가 나지 않도록 `long`/`int` 연산 범위를 관리하는 것은 이후 과제로 남겼다.

### 관련 이슈
[ISSUE-08](../../issues/ISSUE-08_product_stock_can_go_negative.md)
