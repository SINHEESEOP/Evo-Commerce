# [Bug] 알림 저장 실패가 결제 응답에는 전혀 드러나지 않고 조용히 사라짐

### 증상
- 결제 승인(또는 웹훅) 처리는 정상적으로 `200 OK`와 함께 `status: PAID`를 응답한다.
- 그런데 알림 저장(`NotificationRepository.save()`) 과정에서 예외가 나도, 그 예외가 API 응답이나 호출자 어디에도 드러나지 않는다. 클라이언트도, `OrderFacade`도, 이 실패를 전혀 알 수 없다.
- 애플리케이션 로그에만 `SimpleAsyncUncaughtExceptionHandler`가 남기는 에러 로그가 찍힐 뿐이다.

### 환경
- `src/main/java/com/evo/commerce/domain/notification/NotificationEventListener.java`
- `src/main/java/com/evo/commerce/domain/order/OrderFacade.java` (`confirmPayment`, `handlePaymentWebhook`)
- `src/main/java/com/evo/commerce/global/config/AsyncConfig.java`

### 재현 절차
1. `NotificationRepository.save()`가 예외를 던지도록 만든 상태에서(테스트에서는 `@MockitoBean`으로 스텁, 실제 환경이라면 DB 제약 위반이나 일시적 커넥션 장애로 발생할 수 있다) 결제 웹훅을 처리한다.
   ```java
   given(notificationRepository.save(any())).willThrow(new RuntimeException("알림 저장 실패 가정"));

   assertThatCode(() -> orderFacade.handlePaymentWebhook(request)).doesNotThrowAnyException();   // 통과한다
   ```
2. `handlePaymentWebhook()` 호출 자체는 예외 없이 정상 종료된다.
3. 주문을 다시 조회하면 `status: PAID`로 정상 반영돼 있다 — 결제 처리 자체는 완전히 성공한 것으로 보인다.
4. 하지만 `notificationRepository.save()`는 분명히 호출됐고(`verify(notificationRepository, timeout(2000)).save(any())`) 예외까지 던졌는데, 그 사실을 확인할 방법이 API 응답에는 없다.

`OrderPaidEventAsyncFailureTest`가 이 시나리오를 그대로 재현하는 회귀 테스트다.

### 관찰
- `NotificationEventListener.handleOrderPaid()`는 `@Async`로 선언돼 있고 반환 타입이 `void`다.
- 스프링의 `@Async` 메서드는 별도 스레드에서 실행된다. 반환 타입이 `void`인 `@Async` 메서드가 예외를 던지면, 그 예외는 호출자(원래 스레드)로 전파될 방법이 없다 — 이미 원래 스레드는 그 메서드 호출을 "던져두고" 다음 코드로 넘어간 뒤이기 때문이다.
- 스프링은 이런 예외를 `AsyncUncaughtExceptionHandler`(기본 구현은 `SimpleAsyncUncaughtExceptionHandler`)에 넘기는데, 기본 동작은 로그를 남기는 것뿐이다. 이 프로젝트는 커스텀 핸들러를 등록하지 않았으므로 기본 동작 그대로다.
- 결과적으로 "결제는 성공했는데 알림 발송은 실패한" 상황이, 알림이 실패했다는 사실 자체를 아무도(클라이언트, 호출한 서비스 코드, 별도 알람 체계) 알 수 없는 채로 로그 한 줄에 묻힌다.

### 상태
`[CLOSED]`

### 원인 분석
`@Async` 메서드는 별도 스레드에서 실행되며, 호출자는 이벤트를 발행한 시점에 이미 자기 책임을 다한 것으로 보고 응답을 끝낸다. 반환 타입이 `void`라 예외를 돌려줄 통로가 없고, 커스텀 `AsyncUncaughtExceptionHandler`를 등록하지 않아 스프링 기본 구현(로그만 남김)이 그대로 쓰이고 있었다. 그 결과 알림 저장 실패가 로그 한 줄 밖으로는 전혀 드러나지 않았다.

### 해결 방안
`AsyncConfig`가 `AsyncConfigurer`를 구현하도록 바꾸고 `getAsyncUncaughtExceptionHandler()`를 오버라이드해, 실패한 메서드명과 인자를 구조화된 형태로 남기는 커스텀 핸들러를 등록했다. 반환 타입을 `CompletableFuture`로 바꾸는 방식은 호출자가 그 값을 기다리지 않는 fire-and-forget 설계라 실질적 효과가 없어 기각했고, 자동 재시도/아웃박스 패턴은 알림이 유일한 정보 전달 경로가 아니라는 점에서 현재 범위를 벗어난다고 판단해 보류했다. 자세한 논의는 `docs/retrospectives/Step_2.6_리뷰.md` 참고.
