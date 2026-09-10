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
`[CLOSED]`

### 원인 분석
`OutboxEventPublisher.dispatch()`가 `rabbitTemplate.convertAndSend()`의 리턴(예외 없음)을 "발행 완료"의 근거로 삼았다. `convertAndSend()`의 기본 동작은 메시지를 로컬 AMQP 채널에 써 보내는 것까지만 책임지며, 브로커가 그 메시지를 실제로 수신·라우팅했는지는 별도의 확인 절차(퍼블리셔 컨펌) 없이는 알 수 없다. 게다가 발행(publish)과 소비(consume)는 서로 다른 스레드에서 비동기로 분리되어 있어, 컨슈머(`OrderPaidMessageListener`)가 나중에 예외를 던져도 그 사실이 발행자 쪽으로 전파될 방법이 없다.

여기에 `RabbitMQConfig`가 선언한 큐에 Dead Letter Exchange가 없고 컨슈머 쪽에도 재시도 한도가 없어서, 영구적으로 실패하는 메시지(예: 존재하지 않는 사용자 참조)가 Spring AMQP 기본 에러 핸들러(`ConditionalRejectingErrorHandler`)의 `basic.reject(requeue=true)` 동작에 의해 같은 큐로 무한히 되돌아왔다.

### 해결 방안
두 층에서 나눠 해결했다.

1. **발행 확인**: `RabbitTemplate.invoke(operations -> { convertAndSend(...); waitForConfirmsOrDie(timeout); ... })` 패턴으로 바꿔, 브로커가 메시지를 실제로 컨펌한 뒤에만 outbox를 `SENT`로 확정하도록 했다. 컨펌이 타임아웃되거나 nack되면 예외가 발생해 기존 `catch` 블록이 그대로 잡아 `PENDING`으로 남긴다. `RabbitTemplate`에 `mandatory(true)` + `ReturnsCallback`도 추가해, 라우팅 자체가 안 되는 메시지를 로그로 가시화했다.
2. **컨슈머 재시도 한도 + DLQ**: `order.paid.queue`에 `x-dead-letter-exchange`/`x-dead-letter-routing-key`를 지정해 `order.paid.queue.dlq`로 격리 경로를 만들고, `spring.rabbitmq.listener.simple.retry`로 최대 5회(1초 → 2배씩 증가, 최대 10초) 재시도 후 소진되면 `RejectAndDontRequeueRecoverer`가 메시지를 거부(requeue 안 함)해 DLQ로 보내도록 했다. 이제 영구 실패 메시지는 무한 루프 대신 최대 몇 차례의 재시도 뒤 DLQ에서 멈춘다.

의사결정 배경과 검토했던 대안은 [`docs/decisions/015_rabbitmq_publish_confirmation_and_retry_policy.md`](../docs/decisions/015_rabbitmq_publish_confirmation_and_retry_policy.md) 참고.
