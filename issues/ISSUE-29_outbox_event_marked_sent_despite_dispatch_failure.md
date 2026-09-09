# [Bug] 아웃박스 이벤트 발행이 실패해도 상태가 SENT로 갱신되어 메시지가 유실됨

### 증상
- `OutboxEventPublisher.publishPendingEvents()`가 대기 중인(`PENDING`) 이벤트를 처리한 뒤, 실제 알림 저장이 전혀 일어나지 않았는데도 해당 이벤트 상태가 `SENT`로 바뀐다.
- 존재하지 않는 사용자 ID를 담은 `OutboxEvent`(예: `{"orderId":1,"userId":99999}`)를 넣고 스케줄러 메서드를 직접 호출하면, `NotificationEventListener.handleOrderPaid()`가 `BusinessException(USER_NOT_FOUND)`를 던지지만 이 예외는 어디에도 잡히지 않은 채 스프링의 기본 비동기 예외 핸들러(`SimpleAsyncUncaughtExceptionHandler`) 로그로만 남고, `OutboxEventPublisher`가 남기는 에러 로그(`아웃박스 이벤트 발행 중 오류가 발생했습니다`)는 전혀 찍히지 않는다.
- 그 상태에서 `OutboxEvent`를 다시 조회하면 `status`가 이미 `SENT`다. 이후 스케줄러가 몇 번을 더 돌아도 이 이벤트는 `PENDING` 목록에 잡히지 않으므로 다시는 재시도되지 않는다 — 알림은 영구히 유실된다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/infrastructure/OutboxEventPublisher.java` (`publishPendingEvents()`)
- `src/main/java/com/evo/commerce/domain/order/domain/OutboxEvent.java` (`markAsSent()`)
- `src/main/java/com/evo/commerce/domain/notification/application/NotificationEventListener.java` (`@Async @EventListener handleOrderPaid()`)
- `src/main/java/com/evo/commerce/domain/order/application/OrderFacade.java` (`markOrderAsPaid()` — 주문 결제 완료 시 `OutboxEvent`를 `PENDING` 상태로 저장)

### 재현 절차
1. 애플리케이션을 기동한 상태에서, 존재하지 않는 `userId`를 참조하는 `OutboxEvent`를 직접 저장한다.
   ```java
   outboxEventRepository.save(OutboxEvent.builder()
           .eventType("ORDER_PAID")
           .payload("{\"orderId\":1,\"userId\":99999}")
           .status(OutboxEventStatus.PENDING)
           .build());
   ```
2. `OutboxEventPublisher.publishPendingEvents()`를 호출한다(스케줄러의 `@Scheduled` 주기를 기다리지 않고 직접 호출해도 동일하게 재현된다).
3. 잠시 후(비동기 리스너 실행 대기) 로그를 확인한다.
   ```
   ERROR --- [ SimpleAsyncTaskExecutor-1] o.s.a.i.SimpleAsyncUncaughtExceptionHandler : Unexpected exception occurred invoking async method ...
   com.evo.commerce.global.exception.BusinessException: 존재하지 않는 사용자입니다.
   ```
4. 방금 저장한 `OutboxEvent`를 다시 조회한다.
   ```java
   outboxEventRepository.findById(eventId).orElseThrow().getStatus(); // SENT
   ```
5. `notifications` 테이블에는 해당 사용자에 대한 행이 전혀 생성되지 않았음을 확인한다.

### 관찰
- `OutboxEventPublisher.publishPendingEvents()`는 `eventPublisher.publishEvent(payload)` 호출을 `try` 블록 안에 두고, 예외가 나면 로그만 남기는 `catch`를 거쳐 `finally`에서 무조건 `event.markAsSent()`를 호출한다.
- 그런데 `NotificationEventListener.handleOrderPaid()`는 `@Async`로 선언되어 있다. `@Async` 메서드는 호출자 스레드가 아니라 별도의 스레드 풀에서 실행되므로, 그 메서드 내부에서 던져진 예외는 호출자(`eventPublisher.publishEvent(...)`를 호출한 스케줄러 스레드)로 전파되지 않는다. 즉 `publishEvent(...)` 호출 자체는 리스너의 성공/실패와 무관하게 항상 예외 없이 즉시 리턴한다.
- 결과적으로 `OutboxEventPublisher`의 `try-catch`는 리스너 쪽 실패를 절대 잡을 수 없는 죽은 코드이고, `finally` 블록은 실제 알림 저장 성공 여부와 무관하게 매번 이벤트를 `SENT`로 확정해버린다.
- 심지어 `try` 블록 자체에서 예외가 나는 경우(예: `payload`가 깨진 JSON이라 `objectMapper.readValue`가 실패하는 경우)조차, 로그만 남기고 그대로 `SENT`로 넘어가 재시도 기회 자체가 사라진다.
- Outbox 패턴을 도입한 목적이 "인메모리 이벤트 발행이 유실될 수 있다"는 기존 문제(`OrderPaidEventAsyncFailureTest`가 검증하던 바로 그 문제)를 DB에 영속화된 로그로 해결하는 것이었는데, 발행 스케줄러가 성공/실패를 구분하지 못하고 무조건 완료 처리를 해버리면서 같은 유실 문제가 형태만 바뀐 채 그대로 남아 있다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
