# 트랜잭셔널 아웃박스 패턴이 보장하는 것과 보장하지 않는 것

## 문제 상황

주문 결제가 완료되면 사용자에게 알림을 보내야 한다. 초기 구현은 결제 완료 처리와 같은 트랜잭션 안에서 스프링 애플리케이션 이벤트(`ApplicationEventPublisher`)를 발행하고, `@Async` 리스너가 알림을 저장하는 구조였다. 이 구조에는 두 가지 손실 지점이 있었다.

첫째, 이벤트 발행 자체가 인메모리에서 끝난다 — 애플리케이션이 재시작되거나 리스너가 예외를 던지면 그 이벤트는 재시도할 방법 없이 사라진다. 둘째, `@Async` 리스너 내부에서 던져진 예외는 호출자 스레드로 전파되지 않으므로, 발행자는 리스너가 실제로 성공했는지 실패했는지 알 방법이 없다.

## 원인 분석

이 문제는 "이중 쓰기(dual write) 문제"로 알려져 있다: 업무 데이터(결제 완료 처리)와 그 결과로 파생되는 후속 작업(알림 발송)을 서로 다른 두 시스템(RDB, 인메모리 이벤트 버스)에 각각 커밋해야 하는데, 두 커밋을 하나로 묶는 분산 트랜잭션이 없으면 한쪽만 성공하고 다른 쪽이 실패하는 상황을 피할 수 없다.

## 검토한 대안

- **2단계 커밋(2PC)**: 두 시스템에 걸친 트랜잭션을 진짜로 원자화한다. 구현·운영 복잡도가 높고, 참여 시스템 모두가 분산 트랜잭션을 지원해야 한다는 전제가 필요해 배제했다.
- **트랜잭셔널 아웃박스 패턴**: 후속 작업을 곧바로 실행하는 대신, "이 작업을 나중에 실행해야 한다"는 사실 자체를 업무 데이터와 **같은 로컬 DB 트랜잭션** 안에서 별도 테이블(아웃박스)에 기록한다. 그 트랜잭션이 커밋되면 업무 데이터와 아웃박스 기록은 항상 함께 존재하거나 함께 존재하지 않는다 — RDB 하나의 트랜잭션 원자성만으로 이중 쓰기 문제를 우회한다. 이후 별도의 폴링 프로세스가 아웃박스에 쌓인 기록을 읽어 실제 후속 작업(메시지 발행)을 실행한다.

## 적용한 해결책

### 1단계 — 아웃박스 테이블과 폴링 발행자 도입

결제 완료 처리 메서드 안에서, `Payment` 저장과 함께 `OutboxEvent`(상태: `PENDING`)를 같은 트랜잭션에 저장하도록 바꿨다.

```java
private void markOrderAsPaid(Order order, ...) {
    decreaseStockForItems(order);
    order.pay();
    paymentRepository.save(Payment.builder()...build());

    OrderPaidEvent event = new OrderPaidEvent(order.getId(), order.getUser().getId());
    outboxEventRepository.save(OutboxEvent.builder()
            .eventType("ORDER_PAID")
            .payload(toPayload(event))
            .status(OutboxEventStatus.PENDING)
            .build());
}
```

`@Scheduled` 폴링 발행자가 주기적으로 `PENDING` 이벤트를 읽어 실제 발행을 시도한다. 이 시점부터 "결제 완료 사실이 DB에 영속화됐는가"와 "그 사실이 실제로 후속 채널에 전달됐는가"가 분리된 두 단계가 된다.

### 2단계 — 완료 확인 없는 낙관적 완료 처리

처음 만든 폴링 발행자는 발행을 시도한 뒤 성공 여부와 무관하게 곧바로 이벤트를 `SENT`로 확정했다. 원인은 두 가지가 겹쳐 있었다.

> [!WARNING]
> `@Async` 리스너 내부의 예외는 호출자 스레드로 전파되지 않는다. `eventPublisher.publishEvent(payload)` 호출 자체는 리스너의 성공/실패와 무관하게 항상 예외 없이 즉시 리턴하므로, 발행자 쪽 `try-catch`는 애초에 아무것도 잡을 수 없는 죽은 코드였다.

그 상태에서 `finally { event.markAsSent(); }`가 실행 결과와 무관하게 항상 완료 처리를 해버려, 저장에 실패한 이벤트도 다시는 재시도되지 않고 영구히 사라졌다 — 아웃박스를 도입한 이유였던 "유실 방지"가, 발행자 자신의 낙관적 완료 처리 때문에 형태만 바뀐 채 그대로 재발한 것이다.

해결은 `@Async`를 제거해 리스너를 발행자와 같은 스레드·같은 트랜잭션에서 동기 실행되도록 하고, 이벤트별로 독립된 트랜잭션 안에서 "발행 + 완료 처리"를 함께 커밋하는 것이었다.

```java
private void dispatch(OutboxEvent event) {
    try {
        OrderPaidEvent payload = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(payload);
            event.markAsSent();
            outboxEventRepository.save(event);
        });
    } catch (Exception e) {
        log.error("아웃박스 이벤트 발행에 실패했습니다. 다음 스케줄에서 재시도합니다.", e);
    }
}
```

이제 리스너가 예외를 던지면 이 트랜잭션 전체가 롤백되어 완료 처리도 함께 취소되고, 이벤트는 `PENDING`으로 남아 다음 주기에 재시도된다.

### 3단계 — 발행과 소비의 비동기 분리, 그리고 RabbitMQ로 전환

인메모리 이벤트 버스를 실제 메시지 브로커(RabbitMQ)로 바꾸면서 같은 문제가 다른 형태로 다시 나타났다. `rabbitTemplate.convertAndSend(...)`가 예외 없이 반환되는 것은 "메시지를 로컬 채널에 써 보냈다"는 뜻일 뿐, 브로커가 그 메시지를 실제로 받아 라우팅했는지, 나아가 컨슈머가 그 메시지를 끝까지 처리했는지와는 무관하다 — 발행과 소비는 서로 다른 스레드에서 서로 다른 시점에 일어나는 완전히 분리된 두 이벤트이기 때문이다.

여기에 큐 설정에 Dead Letter Exchange도 컨슈머 쪽 재시도 한도도 없었던 탓에, 존재하지 않는 사용자를 참조하는 것처럼 영구적으로 실패하는 메시지가 Spring AMQP 기본 에러 핸들러의 재큐잉 동작에 의해 같은 큐를 무한히 맴도는 문제까지 겹쳤다.

두 층으로 나눠 해결했다.

1. **발행 확인**: `RabbitTemplate.invoke(operations -> { convertAndSend(...); waitForConfirmsOrDie(timeout); })`로 바꿔, 브로커가 메시지를 실제로 컨펌한 뒤에만 완료 처리하도록 했다. 컨펌이 타임아웃되거나 nack되면 예외가 발생해 `PENDING` 상태로 남는다.
2. **컨슈머 재시도 한도 + DLQ**: 큐에 Dead Letter Exchange를 지정하고, 최대 5회(1초 → 지수 백오프, 최대 10초) 재시도 후에도 실패하면 재큐잉 없이 DLQ로 격리하도록 했다. 영구 실패 메시지가 무한 루프 대신 유한한 재시도 뒤 DLQ에서 멈춘다.

```java
private void publishAndAwaitBrokerConfirm(String exchange, String routingKey, Object payload) {
    rabbitTemplate.invoke(operations -> {
        operations.convertAndSend(exchange, routingKey, payload);
        operations.waitForConfirmsOrDie(PUBLISHER_CONFIRM_TIMEOUT_MILLIS);
        return null;
    });
}
```

## 결과

여기까지의 아웃박스+RabbitMQ 조합은 "업무 데이터 저장"과 "그 사실을 외부에 알리는 것"을 안정적으로 분리해줬다. 업무 데이터는 항상 로컬 트랜잭션 안에서 먼저 확정되고, 그 사실을 아웃박스에 남기는 것도 같은 트랜잭션이므로, 두 기록은 항상 함께 존재하거나 함께 존재하지 않는다. 발행 확인과 재시도/DLQ까지 갖추면서 "메시지가 브로커에 전달됐다고 착각하고 완료 처리해버리는" 실패 지점도 막았다.

## 일반화할 수 있는 점 — 그리고 이 패턴이 전제로 삼는 것

이 패턴이 원자성을 보장하는 근거를 다시 보면, 언제나 다음 전제 위에 서 있었다: **업무 행위 자체가 로컬 DB 트랜잭션 안에서 일어나고, 아웃박스 기록은 바로 그 트랜잭션에 함께 묶인다.** 업무 데이터와 아웃박스 기록이 "같은 데이터베이스, 같은 트랜잭션"이라는 사실 하나가 이중 쓰기 문제를 풀어주는 유일한 열쇠였다.

이 프로젝트에서는 이후 참여 동시성 제어를 재설계하면서, 처리량을 위해 "잔여 수량 확인·중복 확인·차감"이라는 업무 행위 자체를 DB 트랜잭션 바깥, Redis의 단일 원자적 연산(Lua 스크립트)으로 옮겼다. 이 결정 자체는 유효했다 — DB 락 경합과 무관하게 참여 확정을 처리할 수 있게 됐다. 하지만 그 직후에 이어지는 아웃박스 기록은, 더 이상 "이미 확정된 업무 데이터 옆에 나란히 남기는 안전한 로그"가 아니라 "이미 다른 시스템에서 벌어진 사실을 뒤늦게 DB에 반영하는, 실패해도 되돌릴 수단이 없는 마지막 관문"으로 성격이 바뀌어 있었다. 아웃박스 기록이 실패하면 Redis 쪽 차감은 이미 커밋된 채로 남고, 그 사용자는 중복 참여 방지 로직 때문에 다시 시도할 수도 없다 — 정원 하나가 어떤 주문 기록도 남기지 못한 채 조용히 사라진다.

이 간극을 메우는 방법은 재시도가 아니라 **보상(compensation)**이다. 아웃박스 기록이 실패하면, 그 실패를 로그로만 남기는 대신 방금 Redis에 확정했던 차감·중복 등록을 명시적으로 되돌리는 별도의 연산(보상 트랜잭션)을 실행해야 한다.

```java
try {
    publishParticipationRequested(eventId, userId);
} catch (RuntimeException e) {
    releaseParticipation(event, userId); // Redis 쪽 차감을 명시적으로 되돌린다
    throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
}
```

정리하면 이렇다.

- 트랜잭셔널 아웃박스 패턴은 "업무 행위와 그 기록이 같은 로컬 트랜잭션에 있다"는 전제 위에서만 원자성을 보장한다. 이 전제가 깨지는 순간(업무 행위가 다른 시스템에서 먼저 확정되는 순간), 아웃박스는 더 이상 안전망이 아니라 새로운 단일 실패 지점이 된다.
- 이종 시스템(Redis, RDB, 메시지 브로커) 사이의 정합성 문제는 어느 한쪽의 원자성만으로는 끝까지 해결되지 않는다 — 각 단계가 "성공했을 때"뿐 아니라 "그 이후 단계가 실패했을 때 누가 되돌릴 책임을 지는가"까지 설계에 포함해야 한다.
- 실패를 다루는 방식은 그 실패의 성격에 따라 달라야 한다. 일시적 실패(DB 커넥션 순간 고갈)는 재시도로 흡수할 수 있지만, 참조 대상이 애초에 존재하지 않는 것 같은 영구적 실패는 재시도해도 결과가 바뀌지 않는다 — 이 둘을 구분하지 않으면 컨슈머는 성공할 수 없는 일에 자원을 계속 낭비하게 된다.
