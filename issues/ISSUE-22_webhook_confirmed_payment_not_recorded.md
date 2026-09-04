# [Bug] 웹훅으로 결제 완료된 주문에는 결제 기록이 남지 않음

### 증상
- Toss 결제 승인 API 응답을 받아 직접 확정한 주문은 `payments` 테이블에 결제수단/승인금액/승인시각이 정상적으로 저장된다.
- 반면 Toss 웹훅(`POST /api/webhooks/toss`)으로 결제 완료 통지를 받아 처리된 주문은 `orders.status`는 정상적으로 `PAID`로 바뀌지만, 같은 주문에 대한 `payments` 테이블 조회 결과가 비어 있다.
- 결과적으로 어떤 경로로 결제가 확정됐는지에 따라 결제수단/승인시각 조회가 가능한 주문과 불가능한 주문이 뒤섞여 존재한다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/application/OrderFacade.java`
- `src/main/java/com/evo/commerce/domain/payment/domain/Payment.java`

### 재현 절차
1. 로그인 후 주문을 생성한다(`status: CREATED`).
2. 아래 요청으로 결제 완료 웹훅을 보낸다.
   ```bash
   curl -X POST http://localhost:8080/api/webhooks/toss \
     -H "Content-Type: application/json" \
     -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"test-payment-key","orderId":"ORDER-{주문ID}","status":"DONE"}}'
   ```
3. 주문을 다시 조회하면 `status: PAID`로 정상 확인된다.
4. 같은 주문 id로 `payments` 테이블을 조회하면 저장된 행이 없다.
5. 대조군: 같은 흐름을 웹훅 대신 `POST /api/orders/{id}/payments/confirm`으로 진행하면 `payments` 테이블에 정상적으로 행이 쌓인다.

### 관찰
- `OrderFacade.confirmPayment()`에는 Toss 승인 응답을 `paymentRepository.save(...)`로 저장하는 코드가 있다.
- `OrderFacade.handlePaymentWebhook()`은 같은 목적(주문을 `PAID`로 전환)의 메서드이지만 이 저장 호출이 없다.
- 두 메서드 모두 "주문 조회 → 재고 차감 → `order.pay()` → 이벤트 발행" 순서가 거의 동일해 보여서, 부수 효과 하나가 한쪽에만 구현돼 있다는 사실이 코드를 훑어보는 것만으로는 잘 드러나지 않는다.

### 상태
`[OPEN]`

### 원인 분석
(추후 작성)

### 해결 방안
(추후 작성)
