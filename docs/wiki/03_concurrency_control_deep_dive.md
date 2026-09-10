# 선착순 이벤트 참여 동시성 제어 전략 비교와 성능 개선

## 문제 상황

타임세일 이벤트 참여 API는 `participantLimit`(선착순 인원)을 초과해서 참여 기록이 저장되면 안 된다. 초기 구현은 참여자 수를 조회(`COUNT`)해서 상한과 비교한 뒤 저장하는 구조였다.

```java
long currentParticipants = timeSaleParticipationRepository.countByTimeSaleEvent(event);
if (currentParticipants >= event.getParticipantLimit()) {
    throw new BusinessException(TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED);
}
// ... 참여 기록 저장
```

`participantLimit = 3`인 이벤트에 서로 다른 사용자 10명이 동시에 참여를 시도하는 테스트를 돌리면, 매번 예외 없이 10명 전원의 참여가 성공했다.

```
동시에_여러_명이_참여해도_선착순_인원_제한을_초과하지_않는다() FAILED
org.opentest4j.AssertionFailedError:
    expected: 3
     but was: 10
```

## 원인 분석

`COUNT` 조회와 `INSERT` 저장이 하나의 원자적 연산으로 묶여 있지 않았다. 두 단계 사이에 다른 트랜잭션이 끼어들 수 있는 시간 간격(check-then-act)이 존재했다.

> [!IMPORTANT]
> 이 메서드는 이미 트랜잭션(`@Transactional`) 안에 있었다. 문제는 "트랜잭션으로 안 묶었다"가 아니라, 트랜잭션의 원자성(ACID의 A — 내 트랜잭션 안의 SQL문이 전부 성공하거나 전부 취소된다는 보장)과 여러 트랜잭션 사이의 상호 배제(mutual exclusion — 내가 처리하는 동안 남이 끼어들지 못하게 막는 것)는 전혀 다른 성질이라는 점이다. 트랜잭션 경계는 후자를 보장하지 않는다.

동시에 시작된 10개의 트랜잭션은 각자 자신의 트랜잭션 시작 시점 스냅샷을 본다(MySQL 기본 격리 수준인 REPEATABLE READ). 서로의 커밋을 아직 보지 못한 채로 전부 `count = 0`을 관측하고, 전부 상한 검사를 통과해버린다. 격리 수준을 READ COMMITTED로 낮춰도 `COUNT` 자체가 락을 걸지 않는 조회(non-locking read)라는 사실은 바뀌지 않으므로, 이 결함은 격리 수준 조정만으로는 해결되지 않는다.

## 검토한 대안

- **비관적 락(`SELECT ... FOR UPDATE`)**: 조회 시점에 행을 잠가 다른 트랜잭션의 접근 자체를 막는다. 경합이 극도로 몰리는 짧은 시간대(선착순 이벤트 시작 순간)에는 정합성을 확실하게 지킬 수 있지만, 락을 쥔 트랜잭션이 끝날 때까지 나머지 트랜잭션이 DB 커넥션을 붙잡은 채 대기해야 해서 커넥션 풀 고갈로 이어질 위험이 있다. 이번에는 채택하지 않았다.
- **낙관적 락(`@Version`) + 재시도**: 평소엔 락을 걸지 않다가 저장 시점에 충돌을 감지해 재시도한다. 충돌이 드문 경우에 유리한 전략이다.
- **Redis 분산 락(Redisson)**: 애플리케이션 레이어에서 이벤트별 락을 걸어, 같은 이벤트에 대한 요청을 한 번에 하나씩만 처리한다.

## 적용한 해결책

**1단계 — 낙관적 락.** `TimeSaleEvent`에 `@Version`과 `currentParticipants` 카운터를 추가하고, `increaseParticipant()`가 상한 검사와 카운터 증가를 한 번에 처리하도록 바꿨다.

```java
public void increaseParticipant() {
    if (currentParticipants >= participantLimit) {
        throw new BusinessException(TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED);
    }
    currentParticipants++;
}
```

`@Version`이 걸린 엔티티를 UPDATE할 때, JPA는 WHERE 절에 버전 값을 자동으로 끼워 넣는다. 두 트랜잭션이 동시에 같은 행을 수정하려 하면, 나중에 커밋을 시도하는 쪽의 UPDATE는 영향받은 행이 0건이 되어 실패한다. 인원 초과 문제는 이걸로 사라졌다.

다만 재시도 로직 없이 이걸 적용하면 새로운 문제가 생겼다. 10개의 트랜잭션이 거의 동시에 같은 행을 `UPDATE`하려 하면 MySQL InnoDB의 행 잠금 순서가 꼬여 데드락이 발생하고, 정원이 남아있었을 요청까지 `CannotAcquireLockException`으로 실패했다. 실측 결과 3명이 성공해야 할 자리에 1명만 성공했다.

**2단계 — 재시도 추가.** `@Retryable`로 `ConcurrencyFailureException`(낙관적 락 충돌과 데드락의 공통 상위 타입)을 최대 5회, 지수 백오프(50ms → 500ms)로 재시도하도록 했다. `@Retryable`과 `@Transactional`을 같은 메서드에 걸면 재시도가 이미 실패가 확정된 영속성 컨텍스트를 재사용해 무의미해지므로, `TransactionTemplate`으로 매 시도마다 새 트랜잭션을 열었다. 이 조치로 매번 정확히 3건이 성공하도록 만들었다.

**3단계 — Redis 분산 락으로 교체.** 정합성은 만족했지만, 이 이벤트별 락 키(`time-sale:participation-lock:{eventId}`)로 `RLock`을 걸어 애초에 동시 접근 자체를 막는 방식으로 전환했다.

```java
public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
    RLock lock = redissonClient.getLock(PARTICIPATION_LOCK_KEY_PREFIX + eventId);
    boolean acquired = lock.tryLock(LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS, TimeUnit.MILLISECONDS);
    if (!acquired) {
        throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
    }
    try {
        return transactionTemplate.execute(status -> participateInNewTransaction(userId, eventId));
    } finally {
        lock.unlock();
    }
}
```

> [!WARNING]
> 락 해제 시점이 중요하다. 이 메서드에 직접 `@Transactional`을 걸면 프록시가 메서드 반환 이후에야 커밋하므로, `finally`의 `unlock()`이 커밋보다 먼저 실행되어 다음 스레드가 아직 반영되지 않은 값을 읽을 수 있다. `TransactionTemplate`으로 트랜잭션을 명시적으로 실행하고, 그 실행이 끝난(=커밋된) 뒤에 락을 반납하도록 순서를 맞췄다.

락이 동시 접근 자체를 막아주므로 `@Retryable`은 더 이상 필요 없어져 제거했다. `@Version`은 락 획득/해제 순서를 잘못 구현하는 실수가 재발했을 때의 마지막 방어선으로 남겨뒀다 — 유지 비용이 사실상 0(추가 컬럼 하나)이라 이점이 명확하다.

## 결과

동일 시나리오(참여 인원 상한 3명, 동시 참여 요청 10건)로 3회 반복 측정했다.

| 전략 | 성공 인원 | 실패 원인 | 평균 소요 시간 |
|---|---|---|---|
| 낙관적 락만(재시도 없음) | 1/3 | 데드락(`CannotAcquireLockException`) 9건 | 결과 불안정 |
| 낙관적 락 + 재시도 | 3/3 | 정상 마감(`PARTICIPANT_LIMIT_EXCEEDED`) 7건 | 약 596ms |
| Redis 분산 락 | 3/3 | 정상 마감(`PARTICIPANT_LIMIT_EXCEEDED`) 7건 | 약 169ms |

Redis 분산 락 쪽이 약 3.5배 빠르다. 낙관적 락+재시도는 10개 요청 전부를 일단 MySQL까지 들여보낸 뒤, 대부분을 롤백시키고 백오프 대기 후 재시도하는 과정을 반복한다 — 이 롤백과 대기 자체가 순수한 낭비 시간이다. Redis 분산 락은 애플리케이션 레이어에서 한 번에 한 요청만 임계 구역에 들여보내므로, MySQL은 애초에 동시 쓰기 경합을 겪지 않는다.

## 일반화할 수 있는 점

- 트랜잭션 경계(원자성)와 동시성 제어(상호 배제)는 서로 다른 문제다. 하나가 있다고 다른 하나가 저절로 해결되지 않는다.
- 정합성을 지키는 전략(낙관적 락)과 그 전략이 만들어내는 부작용(데드락으로 인한 성공률 저하)은 따로 검증해야 한다. "예외가 안 나니까 됐다"가 아니라 "정원 안에서 최대한 많은 요청이 성공하는가"까지 측정해야 진짜 해결 여부를 알 수 있다.
- 충돌을 사후에 감지해서 되돌리는 전략(낙관적 락+재시도)과 충돌 자체를 사전에 막는 전략(분산 락)은 정합성 면에서는 동일한 결과를 낼 수 있어도, 경합이 극도로 몰리는 구간에서는 성능 차이가 크게 벌어질 수 있다. 이 차이는 추측이 아니라 같은 시나리오로 직접 측정해서 확인해야 한다.
- 새 인프라(Redis)를 도입하면 새 장애 지점(SPOF)도 함께 들여오게 된다. 성능 이득과 그 대가를 같이 문서화해야 다음 의사결정에 쓸 수 있다.
