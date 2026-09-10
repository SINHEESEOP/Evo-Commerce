### 문제 상황
`OutboxEventPublisher.dispatch()`가 `rabbitTemplate.convertAndSend()`를 예외 없이 호출했다는 사실만으로 outbox 이벤트를 `SENT`로 확정했다. 이 호출은 브로커가 메시지를 실제로 받아 큐에 반영했는지 확인해주지 않고, 컨슈머 쪽에도 재시도 한도나 Dead Letter Queue가 없어 영구 실패 메시지가 같은 큐를 무한히 맴돌았다(ISSUE-30).

### 검토한 대안
- **퍼블리셔 컨펌을 비동기 콜백(`ConfirmCallback`)으로만 받고, outbox는 그대로 즉시 `SENT` 처리**: 구현이 가장 단순하지만, 콜백이 나중에 도착했을 때 이미 `SENT`로 확정된 outbox row를 다시 `PENDING`으로 되돌리는 로직이 따로 필요해져 상태 전이가 복잡해진다.
- **`RabbitTemplate.invoke(...)` + `waitForConfirmsOrDie(timeout)`으로 컨펌을 동기적으로 기다린 뒤 `SENT` 확정**: 스케줄러가 이벤트를 하나씩 순차 처리하는 배치성 흐름이라, 컨펌을 기다리는 지연이 허용 가능한 범위(초 단위 폴링 주기 안)다.
- **컨슈머 재시도 정책**: (a) 무제한 재시도 + requeue(현재 상태, 무한 루프 원인), (b) 재시도 없이 즉시 `AmqpRejectAndDontRequeueException`으로 DLQ 이동, (c) 제한된 횟수의 지수 백오프 재시도 후 DLQ 이동.

### 결정 및 이유
발행 쪽은 `rabbitTemplate.invoke(...)` + `waitForConfirmsOrDie(5000)`을 선택했다. 콜백 방식은 "컨펌이 나중에 도착했을 때 이미 확정된 상태를 되돌린다"는 동시성 문제를 새로 만들어내는 반면, 동기 대기는 이 스케줄러의 실행 모델(초 단위로 깨어나 이벤트를 순차 처리)과 자연스럽게 맞아떨어지고 상태 전이가 단순해진다. `mandatory(true)` + `ReturnsCallback`도 함께 설정해, 라우팅 자체가 안 되는 경우(바인딩 누락 등)를 로그로 남기도록 했다 — 다만 이 반환 콜백은 outbox 상태 전이에는 관여하지 않는다. 라우팅 실패 시에도 컨펌은 정상적으로 오기 때문에(브로커가 메시지를 받은 것 자체는 사실이므로) `SENT` 판정에 반환 콜백까지 엮으면 상태 전이가 다시 복잡해지고, 이 프로젝트 규모에서는 로그 기반 가시성만으로 충분하다고 판단했다.

컨슈머 쪽은 (c)를 선택했다. (a)는 지금 겪은 문제 그 자체다. (b)는 일시적 장애(DB 커넥션 순간 단절 등)로 인한 실패까지 재시도 기회 없이 곧바로 DLQ로 보내버려, 사람이 개입하지 않으면 복구되지 않는다. 제한된 재시도(최대 5회, 1초부터 시작해 2배씩 늘어나는 백오프, 최대 10초 간격)는 일시적 실패에는 회복 기회를 주고, 영구적 실패(존재하지 않는 사용자 참조 등)는 결국 DLQ로 격리해 무한 루프를 끊는다.

### 적용 결과
`RabbitMQConfig`에 큐 선언 시 `x-dead-letter-exchange`/`x-dead-letter-routing-key`를 지정해 재시도 소진 메시지가 `order.paid.queue.dlq`로 이동하도록 했고, `application.yaml`에 `spring.rabbitmq.listener.simple.retry.*`로 재시도 정책을, `publisher-confirm-type: correlated`로 컨펌을 켰다. 재시도 소진 시 메시지를 DLQ로 격리할 뿐, 그 DLQ에 쌓인 메시지를 모니터링하거나 재처리하는 절차(알림 발송, 운영자 대시보드 등)는 이번 결정 범위에 포함하지 않았다 — 이 프로젝트 규모에서는 RabbitMQ 관리 UI로 직접 확인하는 것으로 충분하다고 보고, 별도 운영 도구는 실제 필요가 생겼을 때 추가하기로 했다.

### 관련 이슈
[ISSUE-30](../../issues/ISSUE-30_outbox_marked_sent_despite_consumer_processing_failure.md)
