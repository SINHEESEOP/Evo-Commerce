# [Bug] Toss 결제창이 orderId 형식 오류로 열리지 않음

### 증상
- `checkout.html`에서 "결제하기" 버튼을 눌러도 아무 반응이 없다 — Toss 결제창이 뜨지 않는다.
- 브라우저 콘솔에 다음 예외가 찍힌다.
  ```
  Uncaught (in promise) Error: `orderId`는 영문 대소문자, 숫자, 특수문자(-, _) 만 허용합니다. 6자 이상 64자 이하여야 합니다.
  ```

### 환경
- `src/main/resources/static/checkout.html`
- `src/main/resources/static/checkout-result.html`
- `src/main/java/com/evo/commerce/domain/order/infrastructure/TossPaymentClient.java`
- `src/main/java/com/evo/commerce/domain/order/application/OrderFacade.java` (`handlePaymentWebhook`)

### 재현 절차
1. 로그인 후 상품 상세에서 주문을 생성한다(예: 주문번호 46).
2. `checkout.html?orderId=46`에서 "결제하기"를 클릭한다.
3. Toss SDK의 `tossPayments.requestPayment('카드', { orderId: String(order.id), ... })` 호출이 `orderId: "46"`을 그대로 넘기는데, Toss 위젯 SDK는 `orderId`가 최소 6자 이상이어야 한다고 자체적으로 검증해 즉시 예외를 던지고 결제창을 열지 않는다.

### 관찰
- 우리 DB의 주문 ID는 자동 증가 정수라서 자릿수가 적은 주문(1~99999)은 전부 이 형식 요구사항(6~64자)에 못 미친다.
- Toss 연동을 처음 붙인 시점(Toss Payments 테스트 연동)부터 있던 문제로 보이며, 지금까지 결제 버튼을 실제로 끝까지 눌러 브라우저에서 검증한 적이 없어 발견되지 않았던 것 같다.
- `orderId`는 결제창을 여는 시점(`requestPayment`), 결제 승인 시점(`TossPaymentClient.confirm`), 웹훅 수신 시점(`OrderFacade.handlePaymentWebhook`) 세 곳에서 각각 Toss와 주고받는 값이라, 한 곳만 포맷을 바꾸면 나머지 두 곳과 불일치가 생긴다.

### 상태
`[CLOSED]`

### 원인 분석
Toss Payments 위젯 SDK는 `orderId`가 6~64자의 영문/숫자/`-`/`_`만 허용한다고 자체적으로 검증한다. 이 프로젝트는 DB의 자동 증가 정수 ID를 그대로 `orderId`로 사용해왔는데, 자릿수가 적은 주문은 이 요구사항을 만족하지 못해 결제창이 열리는 시점에 클라이언트 사이드에서 즉시 거부됐다.

### 해결 방안
`TossPaymentClient`에 `toTossOrderId(Long)`/`parseOrderId(String)` 정적 메서드를 추가해 `"ORDER-{id}"` 형식으로 변환/역변환하는 로직을 한곳에 모았다. `checkout.html`이 결제창을 열 때 `` `ORDER-${order.id}` ``를 넘기도록 바꾸고, `TossPaymentClient.confirm()`이 승인 요청에도 같은 형식을 보내도록 맞췄다(Toss가 승인 시점에 원래 결제창을 열 때 쓴 `orderId`와 일치하는지 검증하므로 두 곳이 반드시 같은 값이어야 한다). `checkout-result.html`은 Toss가 리다이렉트로 돌려주는 `orderId`에서 접두사를 제거해 내부 REST API(`/api/orders/{id}/payments/confirm`)가 기대하는 순수 숫자 ID로 되돌린 뒤 호출한다. 웹훅 쪽(`OrderFacade.handlePaymentWebhook`)도 `TossPaymentClient.parseOrderId()`로 같은 방식으로 접두사를 제거하도록 맞췄다.
