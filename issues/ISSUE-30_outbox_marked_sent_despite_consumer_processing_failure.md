# [Bug] RabbitMQ 컨슈머 처리 실패가 Outbox 완료 상태에 반영되지 않고 메시지가 무한 재전달됨

### 증상
- `OutboxEventPublisherTest.발행에_실패한_이벤트는_대기_상태로_남아_다음_스케줄에서_재시도된다()`가 실패한다.
  ```
  org.opentest4j.AssertionFailedError:
      expected: PENDING
       but was: SENT
  ```
- `OrderPaidEventAsyncFailureTest.알림_저장이_실패해도_웹훅_처리_자체는_예외_없이_끝난다()`도 실패한다.
  ```
  org.mockito.exceptions.verification.WantedButNotInvoked:
  Wanted but not invoked:
  notificationRepository.save(<any>);
  -> at OrderPaidEventAsyncFailureTest.java:121
  Actually, there were zero interactions with this mock.
  ```
- 애플리케이션 로그에 동일한 스택 트레이스가 계속 반복 출력된다.
  ```
  WARN ... ConditionalRejectingErrorHandler : Execution of Rabbit message listener failed.
  ...
  Caused by: com.evo.commerce.global.exception.BusinessException: 존재하지 않는 사용자입니다.
      at OrderPaidMessageListener.lambda$handleOrderPaid$0(OrderPaidMessageListener.java:32)
  ```
  같은 로그 블록이 `redelivered=true` 헤더를 달고 멈추지 않고 재출력된다.

### 환경
- `src/main/java/com/evo/commerce/domain/order/infrastructure/OutboxEventPublisher.java`
- `src/main/java/com/evo/commerce/domain/order/infrastructure/RabbitMQConfig.java`
- `src/main/java/com/evo/commerce/domain/notification/application/OrderPaidMessageListener.java`
- `docker-compose.yml`의 `rabbitmq` 서비스 (`rabbitmq:3.13-management-alpine`, 컨테이너명 `evo-rabbitmq`)

### 재현 절차
1. `docker compose up -d rabbitmq` (또는 `docker compose up -d`)로 브로커를 기동한다.
2. `./gradlew test --tests "com.evo.commerce.domain.order.infrastructure.OutboxEventPublisherTest"` 실행 → 위 증상의 첫 번째 실패 확인.
3. `./gradlew test --tests "com.evo.commerce.domain.order.application.OrderPaidEventAsyncFailureTest"` 실행 → 위 증상의 두 번째 실패 확인.
4. 테스트 실행 중 콘솔 로그에서 동일한 `USER_NOT_FOUND` 스택 트레이스가 반복 출력되는 것을 확인한다.
5. (선택) `http://localhost:15672` (RabbitMQ 관리 UI, guest/guest)에 접속해 `order.paid.queue`를 열고, Redelivered 카운트가 계속 올라가는 것을 관찰한다.

### 관찰
- `OutboxEventPublisher.dispatch()`는 `rabbitTemplate.convertAndSend(...)` 호출이 예외를 던지지 않으면 곧바로 outbox row를 `SENT`로 마감한다.
- `convertAndSend()`가 예외 없이 반환되는 것은 "메시지를 브로커에 큐잉하는 데 성공했다"는 뜻일 뿐이다. 그 메시지를 구독자(`OrderPaidMessageListener`)가 실제로 끝까지 처리했는지와는 아무 관련이 없다 — 발행(publish)과 소비(consume)가 서로 다른 스레드에서, 서로 다른 시점에 비동기로 일어나기 때문이다.
- `RabbitMQConfig`와 `OrderPaidMessageListener` 어디에도 Dead Letter Exchange, 최대 재시도 횟수, 재시도 백오프 설정이 없다. Spring AMQP 리스너 컨테이너의 기본 에러 처리기(`ConditionalRejectingErrorHandler`)는 비즈니스 예외를 "재시도 가능한 실패"로 간주해 메시지를 `basic.reject(requeue=true)`로 큐에 되돌린다. 그 결과 존재하지 않는 사용자를 참조하는 것처럼 영구적으로 실패하는 메시지는 같은 큐를 영원히 맴돈다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
