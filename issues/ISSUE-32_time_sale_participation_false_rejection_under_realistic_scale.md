# [Bug] 참여 정원이 남아있는데도 대규모 동시 요청에서 참여가 거부됨

### 증상
- 정원 100명짜리 타임세일에 100명이 동시에 참여를 요청하면, 낙관적 락+재시도 방식이든 현재 프로덕션의 Redis 분산 락 방식이든 정원의 절반에서 3/4가량이 `PARTICIPATION_TEMPORARILY_UNAVAILABLE`(503)로 거부된다.
- 거부된 요청들의 원인은 `PARTICIPANT_LIMIT_EXCEEDED`(정상 마감)가 아니라 `PARTICIPATION_TEMPORARILY_UNAVAILABLE`이다 — 정원이 실제로 다 차서 거부된 게 아니라, 락/재시도 메커니즘 자체의 한계 때문에 생기는 오탐 거부다. 참여 처리가 다 끝난 뒤 최종 참여 인원(`countByTimeSaleEvent`)은 정원(100)에 한참 못 미친다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` (`participate()`, 현재 프로덕션의 Redis 분산 락 구현)
- `src/main/java/com/evo/commerce/global/config/DataSourceConfig.java`, `src/main/resources/application.yaml` — 이번 Step에서 HikariCP `maximum-pool-size`를 `spring.datasource.master.hikari`/`slave.hikari`에 처음 명시적으로 바인딩했다. 그 전까지는 스프링 부트 기본값(10)이 암묵적으로 적용되고 있었다.
- `src/test/java/com/evo/commerce/domain/timesale/application/benchmark/OptimisticLockRetryParticipationService.java` — Step 3.6에서 Redis 락으로 완전히 교체되기 전 `TimeSaleFacade.participate()`가 실제로 썼던 "낙관적 락 + `@Retryable`" 구현을 재측정을 위해 별도 컴포넌트로 재구현한 테스트 전용 코드(프로덕션 코드에는 없음).
- `src/test/java/com/evo/commerce/domain/timesale/application/benchmark/ParticipationLoadRunner.java`, `TimeSaleParticipationScaleBenchmarkTest.java`, `TimeSaleParticipationScalePoolExpandedBenchmarkTest.java`
- Docker MySQL Master-Slave(`evo-mysql-master`, `evo-mysql-slave`), Redis(`evo-redis`) 컨테이너 위에서 재현.

### 재현 절차
1. `participantLimit = 100`인 `TimeSaleEvent` 1건과 서로 다른 사용자 100명을 준비한다.
2. `CountDownLatch`로 100개 스레드의 시작 시점을 맞춘 뒤, 각 스레드에서 서로 다른 사용자로 동시에 참여를 호출한다 — 낙관적 락+재시도 구현과 현재 Redis 락 구현 각각에 대해, HikariCP `maximum-pool-size`를 10(기본값)과 20 두 경우로 나눠 측정한다.
   ```bash
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationScaleBenchmarkTest"
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationScalePoolExpandedBenchmarkTest"
   ```
3. 두 테스트 모두 "동시 참여자가 많아져도 정원까지는 모두 성공한다"(성공 건수 == 100)를 기대(naive assertion)하지만, 실제로는 다음과 같이 실패한다(재현 테스트는 `@Disabled`로 비활성화해 빌드는 통과하도록 남겨뒀다). 3회 반복 측정한 범위는 다음과 같다.

   | 구현 | HikariCP maximum-pool-size | 성공 | 정상 마감(`PARTICIPANT_LIMIT_EXCEEDED`) | 오탐 거부(`PARTICIPATION_TEMPORARILY_UNAVAILABLE`) |
   |---|---|---|---|---|
   | 낙관적 락 + 재시도 | 10(기본값) | 48~54 | 0 | 46~52 |
   | 낙관적 락 + 재시도 | 20 | 31~36 | 0 | 64~69 |
   | Redis 분산 락(현재 프로덕션) | 10(기본값) | 20~29 | 0 | 71~80 |
   | Redis 분산 락(현재 프로덕션) | 20 | 14~27 | 0 | 73~86 |

   모든 케이스에서 `정상 마감` 건수가 0건이다 — 즉 정원이 실제로 소진돼서 거부된 요청은 하나도 없었다. 실패는 전부 락/재시도 메커니즘의 한계 때문이다.

### 관찰
- **낙관적 락 + 재시도**: `@Retryable`을 재현한 구현은 최대 5회, 50ms→100ms→200ms→400ms→500ms 백오프로 재시도한다. Step 3.6에서는 동시 요청 10건(우연히 HikariCP 기본 풀 크기와 일치)·정원 3명 규모로만 측정해 이 재시도 예산이 항상 충분했다. 하지만 정원 100명 전원이 실제로 경쟁하는 규모(동시 요청 100건)에서는 각 요청이 여러 라운드에 걸쳐 반복적으로 버전 충돌·데드락을 겪고, 5회 재시도 예산을 다 쓰고도 성공하지 못하는 요청이 절반 가까이 발생한다.
- **Redis 분산 락**: `RLock.tryLock(200ms)`의 200ms 대기 시간은 "이벤트 하나당 락 하나"로 완전히 직렬화된 처리량을 감당하기엔 너무 짧다. 참여 처리 1건이 평균 수 ms~수십 ms가 걸리더라도, 100건이 한 줄로 서서 순서대로 처리돼야 하므로 뒤쪽 순번은 200ms 안에 자기 차례가 오지 않아 락 획득 자체에서 탈락한다.
- 두 방식 모두 HikariCP `maximum-pool-size`를 10에서 20으로 늘려도 문제가 해소되지 않는다 — 오히려 낙관적 락+재시도는 동시 경쟁자가 늘어 데드락·버전 충돌이 더 잦아져 성공률이 더 낮아졌다(48~54 → 31~36). 커넥션 풀 크기는 이 결함의 근본 원인이 아니다.
- 두 접근 모두 "동시 접근을 어떻게 순서대로 처리할지"를 재시도(낙관적 락)나 대기(분산 락)라는, 시간 예산이 고정된 수단으로 해결하려 한다는 공통점이 있다. 고정된 시간 예산은 실제 경쟁 규모가 그 예산을 넘어서는 순간 정원과 무관하게 요청을 떨어뜨린다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
