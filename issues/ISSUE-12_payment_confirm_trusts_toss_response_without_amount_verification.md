# [Bug] 결제 승인 처리 시 실제 결제 금액을 서버가 검증하지 않음

### 증상
- 브라우저 개발자 도구 콘솔에서 `checkout.html`이 원래 요청하려던 금액(`order.totalAmount`)보다 낮은 금액으로 `tossPayments.requestPayment()`를 직접 호출해 테스트 결제를 완료한다.
- Toss 결제창은 (요청한 대로) 낮은 금액에 대해 정상적으로 결제를 승인하고, `successUrl`로 `paymentKey`/`orderId`/`amount`(낮은 금액)를 그대로 돌려준다.
- `checkout-result.html`이 이 값 그대로 `POST /api/orders/{orderId}/payments/confirm`을 호출하면, 서버는 200 OK와 함께 `status: PAID`를 응답한다.
- 이후 같은 주문을 다시 조회해도 `status`는 `PAID`로 남아 있고, 별도의 실패나 경고가 없다. 주문에 담긴 상품의 실제 정가 합계(`totalAmount`)보다 적은 금액이 결제됐다는 사실은 어디에도 기록되지 않는다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/OrderFacade.java` (`confirmPayment`)
- `src/main/java/com/evo/commerce/domain/order/TossPaymentClient.java`
- `src/main/resources/static/checkout.html`, `checkout-result.html`

### 재현 절차
1. 로그인 후 상품을 담아 `POST /api/orders`로 주문을 생성한다. 예: 10,000원짜리 상품 2개 → `totalAmount = 20000`.
2. 응답으로 받은 `orderId`로 `/checkout.html?orderId={orderId}`에 접속한다.
3. 페이지가 뜨면 개발자 도구 콘솔을 열고, `결제하기` 버튼을 누르는 대신 다음을 직접 실행한다.
   ```js
   const tossPayments = TossPayments('test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq');
   tossPayments.requestPayment('카드', {
       amount: 100,               // 실제 주문 금액(20000)보다 훨씬 낮은 값
       orderId: String(orderId),
       orderName: '테스트 상품',
       successUrl: location.origin + '/checkout-result.html',
       failUrl: location.origin + '/checkout-result.html',
   });
   ```
4. Toss 테스트 결제창에서 카드 정보를 입력해 100원 결제를 완료한다.
5. `checkout-result.html`로 리다이렉트되면서 서버의 결제 승인 API가 자동 호출된다.
6. `GET /api/orders/{orderId}`로 다시 조회하면 `status: "PAID"`, `totalAmount: 20000`이 그대로 응답된다. 100원만 결제됐다는 사실은 서버 어디에서도 확인되지 않는다.

### 관찰
- `OrderFacade.confirmPayment()`는 `tossPaymentClient.confirm()` 호출이 예외 없이 끝나기만 하면 곧바로 `order.pay()`를 호출한다.
- `tossPaymentClient.confirm()`이 실제로 Toss에 승인 요청을 보낼 때 사용하는 `amount`는 클라이언트가 `PaymentConfirmRequest`에 담아 보낸 값(`request.amount()`) 그대로다. 이 값은 애초에 결제창을 열 때 클라이언트가 지정한 금액과 같으므로, Toss 쪽 승인 자체는 정상적으로 통과한다.
- 즉 Toss는 "결제창을 연 금액"과 "승인 요청 금액"이 서로 일치하는지만 검증할 뿐, 그 금액이 "이 주문이 실제로 지불해야 하는 금액"과 같은지는 알지 못한다. 그 대조는 가맹점 서버(우리 백엔드)의 책임인데, `confirmPayment()` 어디에도 `request.amount()`와 `order.calculateTotalAmount()`를 비교하는 코드가 없다.
- 결과적으로 결제창을 여는 시점의 `amount` 파라미터를 클라이언트가 임의로 조작하면, 그 조작된 금액 그대로 결제가 승인되고 주문도 정상 `PAID`로 전환된다.

### 상태
`[CLOSED]`

### 원인 분석
`OrderFacade.confirmPayment()`가 Toss 승인 API 호출이 예외 없이 끝났다는 사실만으로 곧바로 `order.pay()`를 호출했다. Toss 승인 API는 "결제창을 열 때 지정한 금액"과 "승인 요청 금액"이 서로 일치하는지만 검증하는데, 이 두 금액 모두 클라이언트(브라우저)가 만들어내는 값이다. 즉 Toss의 승인 성공 응답은 "그 금액에 대한 결제가 정상 처리됐다"는 것만 보장할 뿐, "그 금액이 이 주문의 실제 대금과 같다"는 것은 전혀 보장하지 않는다. 그 대조는 가맹점 서버의 책임인데 `confirmPayment()` 어디에도 없었다.

### 해결 방안
`confirmPayment()`에 `order.calculateTotalAmount() != request.amount()` 검증을 추가하고, Toss 승인 API를 호출하기 **전에** 이 검증이 실행되도록 배치했다. 금액이 다르면 `OrderErrorCode.PAYMENT_AMOUNT_MISMATCH`(409)를 던지고 `TossPaymentClient.confirm()`은 아예 호출되지 않는다.

검증 위치를 Toss 호출 이전으로 둔 이유는 두 가지다.
- 승인 API 호출 자체가 네트워크 I/O이자 Toss 쪽에 남는 부수 효과(가맹점 API 호출 로그, 실제 카드사 승인 트랜잭션)이므로, 우리 쪽에서 이미 거부하기로 판단한 요청을 굳이 Toss까지 보낼 이유가 없다.
- Toss 호출 후에 검증하면, 실제로는 Toss가 그 금액을 승인해버린 상태에서 우리 시스템만 주문을 `PAID`로 만들지 않는 불일치가 생긴다(Toss 쪽 승인 취소를 별도로 처리해야 하는 문제로 이어진다). 호출 전에 걸러내면 이런 불일치 자체가 생기지 않는다.

회귀 테스트로 `OrderFacadeTest#결제_금액이_주문_금액과_다르면_예외가_발생하고_Toss_승인을_호출하지_않는다`를 추가했다. 금액이 다른 요청이 예외를 던지는 것뿐 아니라, `TossPaymentClient`가 아예 호출되지 않았다는 것(`verifyNoInteractions`)과 주문 상태가 `CREATED`로 유지된다는 것까지 함께 검증한다.

이미 결제된 주문에 대한 중복 승인 요청(멱등성) 문제는 이번 수정 범위에 포함하지 않았다. `Order.pay()`의 상태 전이 가드가 최종 데이터 정합성은 지켜주지만, 그 앞단에서 불필요한 Toss 재호출까지 막지는 못한다는 한계가 남아 있다.
