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
`[CLOSED]`

### 원인 분석
`handlePaymentWebhook()`이 "이 이벤트를 처음 받는지, 이미 처리한 이벤트의 재전송인지"를 구분하지 않았다. `Order.pay()`의 상태 전이 가드(`status != CREATED`면 예외)는 원래 "잘못된 순서로 상태를 바꾸려는 시도"를 막기 위한 것인데, "이미 같은 이벤트를 처리해서 사실상 아무 문제도 없는 정상적인 재전송"까지 이 가드에 걸려 같은 예외로 처리됐다. 그 예외가 그대로 흘러나가 Toss에게 비-200 응답이 갔고, Toss는 이를 처리 실패로 판단해 재전송을 반복했다.

### 해결 방안
`handlePaymentWebhook()`에서 `order.pay()`를 호출하기 전에 주문이 이미 목표 상태(`PAID`)인지 먼저 확인하도록 했다. 이미 `PAID`면 아무 것도 하지 않고 그대로 반환해, 컨트롤러는 예외 없이 200을 응답한다.

```java
if (!"DONE".equals(request.data().status())) {
    return;
}
if (order.getStatus() == OrderStatus.PAID) {
    return;   // 이미 처리된 이벤트의 재전송 — 조용히 성공 처리
}
order.pay();
```

회귀 테스트로 같은 웹훅을 두 번 처리했을 때 예외 없이 `PAID` 상태가 유지되는지 검증했다. 이벤트 자체에 고유 식별자를 부여해 처리 이력을 추적하는 방식(멱등성 키)도 검토했지만, 지금 웹훅에 딸린 부수 효과가 상태 전이 하나뿐이라 "이미 목표 상태인지" 확인만으로 충분하다고 판단했다. 대안 비교는 `docs/decisions/013_webhook_idempotency_via_target_state_check.md` 참고.

이미 `CANCELLED`인 주문에 결제 완료 웹훅이 오는 경우는 이번 수정 범위 밖이다 — "정상적인 중복 수신"이 아니라 결제와 취소가 경쟁하는 별도의 정합성 문제이므로 여전히 예외를 던진다.
