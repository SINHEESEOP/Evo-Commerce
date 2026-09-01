### 문제 상황
`OrderFacade.confirmPayment()`가 Toss 승인 성공 여부만으로 주문을 `PAID` 처리해, 결제창을 열 때 클라이언트가 넘긴 금액을 조작하면 실제 주문 금액보다 적은 금액으로도 결제가 완료될 수 있었다.

### 검토한 대안
- Toss 승인 API 호출 후, 응답 바디에 포함된 금액 필드를 파싱해 대조
- Toss 승인 API를 호출하기 전에, 우리 시스템이 이미 알고 있는 `order.calculateTotalAmount()`와 요청 금액을 먼저 대조

### 결정 및 이유
후자를 선택했다. `TossPaymentClient.confirm()`은 현재 응답 바디를 파싱하지 않고 상태 코드만 본다(`toBodilessEntity()`) — 이를 위해 응답 파싱을 새로 추가하는 것보다, 이미 갖고 있는 값을 호출 전에 비교하는 쪽이 구현이 단순하다. 또한 호출 후에 검증하면 Toss 쪽에서는 이미 그 금액으로 승인이 끝난 상태에서 우리 시스템만 미결제로 남기는 불일치가 생기지만, 호출 전에 걸러내면 Toss에 요청 자체를 보내지 않으므로 이런 불일치가 생기지 않는다.

### 적용 결과
금액이 불일치하면 `OrderErrorCode.PAYMENT_AMOUNT_MISMATCH`(409)를 던지고 `TossPaymentClient.confirm()`은 호출되지 않는다. 회귀 테스트로 예외 발생, `TossPaymentClient` 미호출(`verifyNoInteractions`), 주문 상태 유지(`CREATED`)를 함께 검증했다.

### 관련 이슈
[ISSUE-12](../../issues/ISSUE-12_payment_confirm_trusts_toss_response_without_amount_verification.md)
