# [Bug] 동시 요청 시 타임세일 선착순 인원 제한이 초과됨

### 증상
- `participantLimit`이 3명으로 설정된 타임세일 이벤트에 10명이 동시에 참여 요청을 보내면, 예외 없이 10명 전원의 참여가 성공하고 `time_sale_participations`에 10건이 저장된다.
- 개별 요청 단위로는 어떤 예외도, 어떤 에러 로그도 남지 않는다. `PARTICIPANT_LIMIT_EXCEEDED` 예외가 던져져야 할 요청들까지 정상 응답으로 처리된다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` (`participate()`)
- `src/main/java/com/evo/commerce/domain/timesale/domain/TimeSaleParticipationRepository.java` (`countByTimeSaleEvent()`)
- `src/test/java/com/evo/commerce/domain/timesale/application/TimeSaleParticipationConcurrencyTest.java`
- Docker MySQL Master-Slave 구성(`evo-mysql-master`, `evo-mysql-slave`) 위에서 재현. `participate()`는 쓰기 트랜잭션이라 Master로 라우팅된다.

### 재현 절차
1. `participantLimit = 3`인 `TimeSaleEvent` 1건과, 서로 다른 사용자 10명을 준비한다.
2. `CountDownLatch`로 10개 스레드의 시작 시점을 맞춘 뒤, 각 스레드에서 서로 다른 사용자로 동시에 `timeSaleFacade.participate(userId, eventId)`를 호출한다.
3. 10개 요청이 모두 끝난 뒤 `timeSaleParticipationRepository.countByTimeSaleEvent(event)`로 최종 참여 인원을 센다.
   ```bash
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.TimeSaleParticipationConcurrencyTest"
   ```
4. 테스트는 `참여 성공 건수 == 3`, `최종 참여 인원 == 3`을 기대(naive assertion)하지만 실제로는 다음과 같이 실패한다.
   ```
   TimeSaleParticipationConcurrencyTest > 동시에_여러_명이_참여해도_선착순_인원_제한을_초과하지_않는다() FAILED
       org.opentest4j.AssertionFailedError:
       expected: 3
        but was: 10
   ```
   10건 요청 전부가 성공해 `participantLimit(3)`을 100% 초과한다 — 일부만 새는 게 아니라 제한 자체가 사실상 걸리지 않는다.

### 관찰
- `TimeSaleFacade.participate()`는 `countByTimeSaleEvent()`로 현재 참여 인원을 조회(check)한 뒤, 그 값을 비교해서 통과하면 별도의 락 없이 `TimeSaleParticipation`을 저장(act)한다 — 조회와 저장이 하나의 원자적 연산이 아니라 두 단계로 분리되어 있다.
- 10개의 동시 트랜잭션이 각자 자신의 트랜잭션을 시작한 시점에는 다른 트랜잭션이 아직 커밋되지 않았으므로, `countByTimeSaleEvent()`가 반환하는 값이 10개 트랜잭션 모두에서 `0`(또는 그와 가까운 낮은 값)으로 관측된다. 그 결과 10개 트랜잭션 전부가 `currentParticipants >= event.getParticipantLimit()` 검사를 통과해버린다.
- 이 경쟁을 막을 수 있는 장치가 코드 어디에도 없다 — `SELECT ... FOR UPDATE` 같은 명시적 락도, `@Version`을 사용한 낙관적 락도, DB 레벨에서 참여 인원 상한을 강제하는 제약 조건(CHECK 제약, 트리거 등)도 없다. `time_sale_participations`에 걸린 유니크 제약은 "동일 사용자의 중복 참여"만 막을 뿐 "전체 참여 인원 상한"과는 무관하다.

### 상태
`[CLOSED]`

### 원인 분석
`countByTimeSaleEvent()`로 인원 수를 세는 시점과 `TimeSaleParticipation`을 저장하는 시점 사이에 시간 간격이 존재했다. 이 메서드는 이미 `@Transactional` 안에 있었지만, 트랜잭션 경계가 보장하는 원자성(내 트랜잭션 안의 SQL문이 전부 성공하거나 전부 취소된다는 것)과 여러 트랜잭션 사이의 상호 배제(내가 처리하는 동안 남이 끼어들지 못하게 막는 것)는 서로 다른 성질이라 트랜잭션만으로는 이 결함이 막히지 않았다. 동시에 시작된 트랜잭션들은 각자 자신의 시작 시점 스냅샷(REPEATABLE READ)만 보므로, 서로의 커밋을 아직 보지 못한 채 전부 낮은 카운트를 관측하고 상한 검사를 통과해버렸다. 격리 수준을 낮춰도 `COUNT` 자체가 락을 걸지 않는 조회라 결함은 사라지지 않는다.

### 해결 방안
2단계로 해결했다.

1. `TimeSaleEvent`에 `@Version`과 `currentParticipants` 카운터를 추가하고, `increaseParticipant()`가 상한 검사와 카운터 증가를 한 번의 엔티티 갱신으로 처리하도록 바꿨다. 인원 초과는 이걸로 사라졌지만, 재시도 로직이 없어 동시 UPDATE가 MySQL InnoDB 데드락으로 이어져 정원이 남았음에도 대부분의 요청이 실패했다([ISSUE-28]로 별도 추적 후 `@Retryable` + `TransactionTemplate`로 해결).
2. 이후 경합이 몰리는 구간의 응답 지연을 줄이기 위해 Redis 분산 락(Redisson)으로 전환했다. 이벤트별 락 키(`time-sale:participation-lock:{eventId}`)로 동시 접근 자체를 애플리케이션 레이어에서 막아, 낙관적 락+재시도 대비 평균 응답 시간이 약 596ms에서 약 169ms로 줄었다(약 3.5배). `@Version`은 락 순서를 잘못 구현했을 때의 마지막 방어선으로 유지했다.

상세 비교와 실측 수치는 [`docs/wiki/03_concurrency_control_deep_dive.md`](../docs/wiki/03_concurrency_control_deep_dive.md) 참고.
