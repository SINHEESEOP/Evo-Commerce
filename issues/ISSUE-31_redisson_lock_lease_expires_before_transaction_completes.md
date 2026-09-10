# [Bug] Redisson 분산 락이 트랜잭션 완료 전에 leaseTime 만료로 조기 해제됨

### 증상
- 타임세일 참여 처리(`TimeSaleFacade.participate`)가 `RLock.tryLock(waitTime=200ms, leaseTime=3000ms)`로 이벤트별 락을 잡고 있음에도, 락을 먼저 획득한 스레드의 트랜잭션이 아직 끝나지 않은 상태에서 뒤이어 요청한 스레드가 같은 락을 획득해버린다.
- 재현 테스트(`TimeSaleParticipationLockLeaseTest`)를 실행하면 아래와 같이 실패한다.
  ```
  java.lang.AssertionError:
  Expecting code to raise a throwable.
  	at com.evo.commerce.domain.timesale.application.TimeSaleParticipationLockLeaseTest
  	    .앞선_참여자의_처리가_오래_걸려도_뒤이은_참여자는_락이_풀릴_때까지_대기한다(TimeSaleParticipationLockLeaseTest.java:140)
  ```
- 같은 실행의 Hibernate SQL 로그를 보면, 먼저 요청한 참여자(`slowUserId`)의 트랜잭션 콜백이 아직 `Thread.sleep()` 중이라 `orders`/`time_sale_participations`에 대한 INSERT가 한 건도 나가지 않은 시점에, 뒤이어 요청한 참여자(`fastUserId`)의 `INSERT INTO orders`, `INSERT INTO order_items`, `INSERT INTO time_sale_participations`, `UPDATE time_sale_events`가 먼저(그리고 유일하게) 실행되어 커밋까지 끝난다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` — `LOCK_LEASE_MILLIS = 3_000`, `LOCK_WAIT_MILLIS = 200`
- `src/main/java/com/evo/commerce/global/config/RedissonConfig.java` — `RedissonClient` 단일 서버 구성, `lockWatchdogTimeout` 관련 설정 없음
- `src/test/java/com/evo/commerce/domain/timesale/application/TimeSaleParticipationLockLeaseTest.java` — 재현 테스트
- Redis 컨테이너(`redis:7.4-alpine`, `docker-compose.yml`의 `redis` 서비스)

### 재현 절차
```bash
docker compose down -v
docker compose up -d mysql-master mysql-slave redis rabbitmq
./gradlew test --tests "com.evo.commerce.domain.timesale.application.TimeSaleParticipationLockLeaseTest"
```
1. 이벤트 정원(`participantLimit`)을 5로 넉넉하게 잡아, 인원 마감으로 인한 정상 실패 가능성을 배제한다.
2. 참여자 A(`slowUserId`)의 요청만 골라 `TransactionTemplate.execute()` 진입 시점에 4000ms를 지연시킨다 — `LOCK_LEASE_MILLIS`(3000ms)보다 긴 트랜잭션을 흉내낸 것이다.
3. 참여자 A의 요청을 별도 스레드에서 먼저 시작시키고, 3300ms(리스 시간 3000ms는 지났지만 A의 인위적 지연 4000ms는 아직 끝나지 않은 시점) 뒤에 참여자 B(`fastUserId`)의 요청을 같은 스레드(테스트 메인 스레드)에서 호출한다.
4. 이 시점에 A의 트랜잭션은 아직 끝나지 않았어야 하므로(`slowFuture.isDone() == false`, 실제로 이 단언은 통과한다), B의 요청은 락을 획득하지 못하고 `BusinessException(PARTICIPATION_TEMPORARILY_UNAVAILABLE)`을 던져야 한다고 가정하고 테스트를 작성했다.

### 관찰
- Redisson의 `tryLock(waitTime, leaseTime, unit)`은 `leaseTime`을 명시적으로 지정하면 내부 워치독(watchdog)에 의한 자동 연장을 사용하지 않는다 — Redis 서버 입장에서 락 키는 정확히 3000ms 뒤에 TTL 만료로 사라진다.
- 반면 Java 스레드(A)는 락을 "논리적으로" 계속 들고 있다고 믿고 4000ms짜리 트랜잭션 콜백을 실행 중이다. Redis 서버는 A의 Java 스레드가 아직 살아있는지, 트랜잭션이 끝났는지 전혀 알지 못한 채 오직 TTL만으로 락의 존재 여부를 결정한다.
- 그 결과 T=3000ms 시점에 Redis 상에서 락 키가 사라지고, T=3300ms에 시도한 B의 `tryLock()`은 A가 여전히 임계 구역 안에 있음에도 즉시 성공한다.
- B는 그대로 `TimeSaleEvent` 조회 → 참여 여부 확인 → `increaseParticipant()` → 주문/참여 기록 저장까지 아무 충돌 없이 커밋한다. A와 B가 같은 이벤트에 대해 "동시에 하나만" 들어가야 한다는 락의 존재 목적 자체가 무너진 것이다.
- 이번 재현에서는 A(아직 저장 전)와 B가 서로 다른 로우를 다루는 시점이 겹치지 않아 `@Version` 충돌이나 데이터 초과 저장까지는 나타나지 않았지만, A의 트랜잭션이 조금 더 진행된 상태에서 B가 끼어들면 ISSUE-28에서 이미 겪었던 `ObjectOptimisticLockingFailureException`이 다시 발생할 수 있는 조건이 그대로 남아 있다.

### 상태
[OPEN]

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
