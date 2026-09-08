# [Bug] 낙관적 락 도입 후 재시도 로직 부재로 동시 참여 요청 대부분이 락 획득 실패로 응답됨

### 증상
- `participantLimit = 3`인 타임세일 이벤트에 10명이 동시에 참여 요청을 보내면, 참여 인원이 3명을 초과하는 문제는 더 이상 발생하지 않는다.
- 그런데 정작 최종적으로 참여에 성공하는 인원이 3명이 아니라 1명뿐이다. 나머지 9건은 `PARTICIPANT_LIMIT_EXCEEDED`(정상적인 마감 응답)가 아니라, 어떤 요청은 락 획득에 실패해 예외가 그대로 흘러나가고 그 예외를 잡아 재응답하는 로직이 없어 500 Internal Server Error로 끝난다.
- 매 요청마다 예외 없이 조용히 실패하는 게 아니라, `[Unhandled Error]` 로그가 9건씩 남는다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/domain/TimeSaleEvent.java` (`increaseParticipant()`, `@Version`)
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` (`participate()`)
- `src/test/java/com/evo/commerce/domain/timesale/application/TimeSaleParticipationConcurrencyTest.java`
- Docker MySQL Master-Slave 구성(`evo-mysql-master`) 위에서 재현. `participate()`는 쓰기 트랜잭션이라 Master로 라우팅된다.

### 재현 절차
1. `TimeSaleParticipationConcurrencyTest`의 `@Disabled` 어노테이션을 제거한다.
2. 아래 명령으로 테스트를 실행한다.
   ```bash
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.TimeSaleParticipationConcurrencyTest"
   ```
3. 매 실행마다 다음과 동일한 결과가 나온다(3회 반복 실행으로 재현성 확인).
   ```
   org.opentest4j.AssertionFailedError:
   expected: 3
    but was: 1
   ```
4. 실패한 9개 요청이 던진 예외를 확인하면 전부 다음과 같다.
   ```
   org.springframework.dao.CannotAcquireLockException: could not execute statement
   [Deadlock found when trying to get lock; try restarting transaction]
   [update time_sale_events set ... where id=? and version=?]
   ```

### 관찰
- `TimeSaleEvent.increaseParticipant()`와 `@Version` 필드 자체는 의도대로 동작한다 — 참여 인원이 `participantLimit`을 초과해서 저장되는 경우는 이제 없다.
- 문제는 `TimeSaleFacade.participate()`가 `event.increaseParticipant()` 호출 이후 발생할 수 있는 낙관적 락 충돌(버전 불일치)이나, 그 이전에 InnoDB가 같은 로우(`time_sale_events` 테이블의 해당 이벤트 행)에 거는 실제 UPDATE 행 잠금 경합으로 인한 데드락을 전혀 처리하지 않는다는 점이다.
- 10개의 트랜잭션이 거의 동시에 같은 이벤트 행을 `UPDATE ... WHERE id=? AND version=?`으로 갱신하려 시도하면, MySQL InnoDB의 잠금 대기 순서가 꼬이면서 일부 트랜잭션이 데드락으로 강제 롤백된다. 이는 "인원이 이미 다 찼다"는 정상적인 비즈니스 실패가 아니라, 순수하게 인프라 레벨의 잠금 경합으로 인한 실패다.
- `TimeSaleFacade.participate()`에는 이 예외를 잡아 재시도하거나, 최소한 `PARTICIPANT_LIMIT_EXCEEDED`와 구분되는 명확한 응답으로 변환하는 로직이 없다. 예외가 그대로 컨트롤러까지 전파되고, `GlobalExceptionHandler`의 `handleUnexpectedException()`(모든 `Exception`을 잡는 catch-all)이 이를 500으로 응답한다.
- 결과적으로 "선착순 인원을 초과하지 않는다"는 요구사항은 만족하지만, "정원 안에서는 최대한 많은 사용자가 성공해야 한다"는 요구사항은 만족하지 못한다 — 사실상 첫 번째로 락을 획득한 1명만 성공하고, 아직 정원이 남아있었을 나머지 2자리는 채워지지 않은 채 요청 자체가 실패로 끝난다.

### 상태
`[CLOSED]`

### 원인 분석
`TimeSaleEvent.increaseParticipant()`와 `@Version`은 "참여 인원이 `participantLimit`을 초과해서 저장되는 것"만 막을 뿐, 그 과정에서 발생하는 DB 잠금 경합이나 데드락을 처리하는 것과는 무관하다. `TimeSaleFacade.participate()`가 `event.increaseParticipant()`를 호출하고 트랜잭션을 커밋하는 흐름에서, 여러 스레드가 같은 `TimeSaleEvent` 로우를 동시에 `UPDATE`하려 하면 InnoDB의 실제 로우 잠금 경합이 일어나고, 이 경합이 데드락으로 이어지면 일부 트랜잭션이 강제로 롤백된다. `participate()`에는 이 실패를 잡아 재시도하는 로직이 전혀 없었기 때문에, 잠금 경합에서 밀린 요청은 아직 정원이 남아있었더라도 그대로 실패로 끝나고, `GlobalExceptionHandler`의 catch-all이 이를 500으로 응답했다.

### 해결 방안
`TimeSaleFacade.participate()`에 Spring Retry의 `@Retryable`을 적용해, `ObjectOptimisticLockingFailureException`과 `CannotAcquireLockException`의 공통 상위 타입인 `org.springframework.dao.ConcurrencyFailureException`을 대상으로 최대 5회, 50ms부터 시작해 두 배씩 늘어나는(최대 500ms) 백오프로 재시도하도록 했다. 재시도를 모두 소진하면 `@Recover` 메서드가 `TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE`(503)이라는, "정원이 다 찼다"(409, `PARTICIPANT_LIMIT_EXCEEDED`)와는 명확히 구분되는 예외로 변환한다.

`@Retryable`과 `@Transactional`을 같은 메서드에 함께 걸면 재시도가 실패가 확정된 영속성 컨텍스트를 그대로 재사용해 무의미해지는 문제가 있어, `participate()`는 `@Transactional`을 직접 갖지 않고 매 재시도마다 `TransactionTemplate.execute(...)`로 새 트랜잭션을 여는 방식을 택했다.

```java
@Retryable(
        retryFor = ConcurrencyFailureException.class,
        maxAttempts = 5,
        backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500)
)
public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
    return transactionTemplate.execute(status -> participateInNewTransaction(userId, eventId));
}

@Recover
public TimeSaleParticipationResponse recoverFromConcurrencyFailure(
        ConcurrencyFailureException e, Long userId, Long eventId) {
    throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
}
```

추가로, `participate()`는 `event.currentParticipants`를 신뢰 소스로 쓰는 반면 `getEvents()`/`getEvent()`는 별도의 `COUNT(*)` 쿼리로 인원을 세고 있어 참여 인원의 출처가 두 곳으로 갈라져 있던 문제도 함께 정리했다. `TimeSaleMapper.toResponse()`가 `event.getCurrentParticipants()`를 직접 읽도록 통일해, 이제 참여 인원 조회 경로가 모두 같은 값을 참조한다.

같은 시나리오(정원 3명, 동시 요청 10건)로 `TimeSaleParticipationConcurrencyTest`를 반복 실행한 결과, 매번 정확히 3건이 성공하고(약 590~605ms 소요) 나머지 7건은 `PARTICIPANT_LIMIT_EXCEEDED`로 정상 종료된다 — 더 이상 500이나 데드락으로 인한 손실 없이 정원 안에서 최대한 성공시킨다.
