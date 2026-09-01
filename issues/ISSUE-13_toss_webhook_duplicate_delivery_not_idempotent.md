# [Bug] Toss 웹훅이 중복 전달되면 예외가 그대로 응답되어 재시도가 계속됨

### 증상
- 결제가 이미 완료돼 주문이 `PAID`인 상태에서, 같은 결제 건에 대한 웹훅 이벤트(`PAYMENT_STATUS_CHANGED`, `status: "DONE"`)가 다시 한번 `POST /api/webhooks/toss`로 들어오면 500이 아니라 409 Conflict가 응답된다.
- Toss는 웹훅 응답이 200 OK가 아니면 실패로 간주하고 재전송을 시도한다. 즉 이미 정상적으로 반영된 이벤트인데도 우리 서버가 매번 409를 돌려주는 한, Toss는 계속 같은 이벤트를 재전송하게 된다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/TossWebhookController.java`
- `src/main/java/com/evo/commerce/domain/order/OrderFacade.java` (`handlePaymentWebhook`)
- `src/main/java/com/evo/commerce/domain/order/Order.java` (`pay()`)

### 재현 절차
1. 로그인 후 주문을 생성한다(`status: CREATED`).
2. 아래 요청으로 첫 번째 웹훅을 보낸다.
   ```bash
   curl -X POST http://localhost:8080/api/webhooks/toss \
     -H "Content-Type: application/json" \
     -d '{"eventType":"PAYMENT_STATUS_CHANGED","data":{"paymentKey":"test-payment-key","orderId":"{주문ID}","status":"DONE"}}'
   ```
   → 200 OK, 주문 상태가 `PAID`로 바뀐다.
3. 같은 요청을 다시 한번 그대로 보낸다(Toss가 재전송하는 상황을 흉내낸 것).
   → 200 OK가 아니라 `409 Conflict`(`OrderErrorCode.INVALID_STATUS_TRANSITION`)가 응답된다.

### 관찰
- `OrderFacade.handlePaymentWebhook()`은 이벤트를 몇 번째로 받았는지, 주문이 이미 처리된 상태인지 확인하지 않고 매번 `order.pay()`를 그대로 호출한다.
- `Order.pay()`는 `status != CREATED`면 예외를 던지도록 이미 가드가 걸려 있다. 이 가드는 "잘못된 상태 전이"를 막기 위한 것인데, 지금은 "이미 같은 이벤트를 처리해서 결과적으로 아무것도 할 필요가 없는 정상적인 중복 수신"까지 같은 예외로 처리해버린다.
- `TossWebhookController`는 이 예외를 그대로 흘려보내고, `GlobalExceptionHandler`가 `OrderErrorCode`에 정의된 HTTP 상태(409)로 변환해 응답한다. Toss 입장에서는 200이 아니므로 실패로 판단하고 재전송하며, 재전송할 때마다 같은 409가 반복된다.

### 상태
`[OPEN]`

### 원인 분석
(해결 후 작성)

### 해결 방안
(해결 후 작성)
