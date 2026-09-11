# [Bug] 참여 정원이 남아있는데도 대규모 동시 요청에서 참여가 거부됨

### 증상
- 정원 100명짜리 타임세일에 실제 규모의 동시 참여 요청이 몰리면, 낙관적 락+재시도 방식이든 현재 프로덕션의 Redis 분산 락 방식이든 정원이 남아있는 상태에서 `PARTICIPATION_TEMPORARILY_UNAVAILABLE`(503)로 거부되는 요청이 발생한다.
- 거부된 요청들의 원인은 `PARTICIPANT_LIMIT_EXCEEDED`(정상 마감)가 아니라 `PARTICIPATION_TEMPORARILY_UNAVAILABLE`이다 — 정원이 실제로 다 차서 거부된 게 아니라, 락/재시도 메커니즘 자체의 한계 때문에 생기는 오탐 거부다.
- 두 가지 서로 다른 동시 요청 규모로 측정했을 때, 두 구현의 우열이 완전히 뒤바뀐다 — 아래 관찰 참고.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` (`participate()`, 현재 프로덕션의 Redis 분산 락 구현)
- `src/main/java/com/evo/commerce/global/config/DataSourceConfig.java`, `src/main/resources/application.yaml` — 이번 Step에서 HikariCP `maximum-pool-size`를 `spring.datasource.master.hikari`/`slave.hikari`에 처음 명시적으로 바인딩했다. 그 전까지는 스프링 부트 기본값(10)이 암묵적으로 적용되고 있었다.
- `src/test/java/com/evo/commerce/domain/timesale/application/benchmark/OptimisticLockRetryParticipationService.java` — Step 3.6에서 Redis 락으로 완전히 교체되기 전 `TimeSaleFacade.participate()`가 실제로 썼던 "낙관적 락 + `@Retryable`" 구현을 재측정을 위해 별도 컴포넌트로 재구현한 테스트 전용 코드(프로덕션 코드에는 없음).
- `src/test/java/com/evo/commerce/domain/timesale/application/benchmark/ParticipationLoadRunner.java`
- `TimeSaleParticipationScaleBenchmarkTest.java`, `TimeSaleParticipationScalePoolExpandedBenchmarkTest.java` — 균형 경쟁 시나리오(정원 100명 = 동시 요청자 100명)
- `TimeSaleParticipationSurgeBenchmarkTest.java`, `TimeSaleParticipationSurgePoolExpandedBenchmarkTest.java` — 수요 폭증 시나리오(정원 100명 vs 동시 요청자 1,000명)
- Docker MySQL Master-Slave(`evo-mysql-master`, `evo-mysql-slave`), Redis(`evo-redis`) 컨테이너 위에서 재현.

### 재현 절차
1. `participantLimit = 100`인 `TimeSaleEvent` 1건을 준비하고, 두 가지 규모로 나눠 `CountDownLatch`로 동시에 참여를 호출한다 — 낙관적 락+재시도 구현과 현재 Redis 락 구현 각각에 대해, HikariCP `maximum-pool-size` 10(기본값)/20 두 경우로 나눠 측정한다.
   ```bash
   # 시나리오 1: 균형 경쟁 - 정원 100명, 동시 요청자 100명 (지원자 전원이 실제로 슬롯을 놓고 경쟁)
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationScaleBenchmarkTest"
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationScalePoolExpandedBenchmarkTest"

   # 시나리오 2: 수요 폭증 - 정원 100명, 동시 요청자 1,000명 (인기 타임세일의 실제 "마감 순간")
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationSurgeBenchmarkTest"
   ./gradlew test --tests "com.evo.commerce.domain.timesale.application.benchmark.TimeSaleParticipationSurgePoolExpandedBenchmarkTest"
   ```
2. 모든 테스트가 "지원자가 아무리 많아져도 정원까지는 모두 성공한다"(성공 건수 == 100)를 기대(naive assertion)하지만, Redis 분산 락은 두 시나리오 모두에서, 낙관적 락+재시도는 시나리오 1에서만 이 기대를 벗어난다(재현 테스트는 실패하는 것만 `@Disabled`로 비활성화해 빌드는 통과하도록 남겨뒀다). 3회 반복 측정한 범위는 다음과 같다.

   **시나리오 1 — 균형 경쟁(정원 100명, 동시 요청자 100명)**

   | 구현 | HikariCP maximum-pool-size | 성공 | 정상 마감 | 오탐 거부 |
   |---|---|---|---|---|
   | 낙관적 락 + 재시도 | 10(기본값) | 48~54 | 0 | 46~52 |
   | 낙관적 락 + 재시도 | 20 | 31~36 | 0 | 64~69 |
   | Redis 분산 락(현재 프로덕션) | 10(기본값) | 20~29 | 0 | 71~80 |
   | Redis 분산 락(현재 프로덕션) | 20 | 14~27 | 0 | 73~86 |

   **시나리오 2 — 수요 폭증(정원 100명, 동시 요청자 1,000명)**

   | 구현 | HikariCP maximum-pool-size | 성공 | 정상 마감 | 오탐 거부 |
   |---|---|---|---|---|
   | 낙관적 락 + 재시도 | 10(기본값) | 100 | 900 | 0 |
   | 낙관적 락 + 재시도 | 20 | 100 | 900 | 0 |
   | Redis 분산 락(현재 프로덕션) | 10(기본값) | 2~9 | 0 | 991~998 |
   | Redis 분산 락(현재 프로덕션) | 20 | 9 | 0 | 991 |

   모든 케이스에서 `정상 마감` 건수는 실제로 성공한 인원만큼만 잡히고, 나머지는 전부 오탐 거부다 — 즉 정원이 진짜로 소진돼서 거부된 요청은 시나리오 2에서 낙관적 락+재시도가 만들어낸 900건뿐이고, 나머지 오탐 거부는 전부 락/재시도 메커니즘 자체의 한계다.

3. 위 표는 성공/거부 "건수"만 담고 있어, 각 요청이 성공/거부 판정을 받기까지 실제로 얼마나 걸렸는지는 드러나지 않는다. `ParticipationLoadRunner`에 요청별 지연 시간(latency) 측정을 추가해 재측정한 결과(각 시나리오의 HikariCP 기본값(10) 케이스, 1회 측정, 단위 ms)는 다음과 같다.

   **시나리오 1 — 균형 경쟁(정원 100명, 동시 요청자 100명)**

   | 구현 | 결과 | 건수 | min | avg | p95 | max |
   |---|---|---|---|---|---|---|
   | 낙관적 락 + 재시도 | 성공 | 54 | 52 | 624.8 | 1297 | 1338 |
   | 낙관적 락 + 재시도 | 오탐 거부 | 46 | 1632 | 1736.8 | 1841 | 1847 |
   | Redis 분산 락 | 성공 | 17 | 66 | 135.1 | 207 | 207 |
   | Redis 분산 락 | 오탐 거부 | 83 | 202 | 204.4 | 205 | 206 |

   **시나리오 2 — 수요 폭증(정원 100명, 동시 요청자 1,000명)**

   | 구현 | 결과 | 건수 | min | avg | p95 | max |
   |---|---|---|---|---|---|---|
   | 낙관적 락 + 재시도 | 성공 | 100 | 20 | 1048.7 | 1894 | 1988 |
   | 낙관적 락 + 재시도 | 정상 마감 | 900 | 1994 | 2313.2 | 2475 | 2620 |
   | Redis 분산 락 | 성공 | 17 | 88 | 181.3 | 206 | 206 |
   | Redis 분산 락 | 오탐 거부 | 983 | 199 | 210.6 | 238 | 247 |

   지연 시간 측정에서 드러나는 사실은 건수 표만으로는 알 수 없던 것이다.
   - **낙관적 락 + 재시도의 오탐 거부는 성공보다 오히려 느리다.** 시나리오 1에서 오탐 거부는 평균 1736.8ms로, 성공(624.8ms)의 약 2.8배다 — 재시도 5회를 실제로 다 소진(`@Retryable`의 지수 백오프 스케줄을 끝까지 채움)한 뒤에야 실패로 확정되기 때문이다. 즉 이 방식은 실패를 빠르게 알려주지 못하고, 오히려 실패할 요청일수록 커넥션을 더 오래 붙잡고 있다가 실패한다.
   - **Redis 분산 락의 오탐 거부 지연 시간은 정확히 `tryLock` 대기 예산(200ms)에 수렴한다.** 시나리오 1·2 모두 오탐 거부 평균이 204~211ms로, `TimeSaleFacade.LOCK_WAIT_MILLIS = 200`(밀리초)에 근접한다 — 즉 거부된 요청은 200ms를 꽉 채워 기다리다가 실패하며, 이 대기 시간은 락 소유자에게 도달하는 실제 대기열 길이와 무관하게 고정돼 있다.
   - **시나리오 2의 낙관적 락 + 재시도는 정상 마감이 성공보다 느리다.** 성공 평균 1048.7ms, 정상 마감(진짜 정원 소진) 평균 2313.2ms — HikariCP 커넥션 풀(10개)에 늦게 진입할수록 그사이 이미 정원이 다 찬 상태를 보게 될 확률이 높아지고, 커넥션을 오래 기다린 요청일수록 뒤늦게 "이미 마감"임을 확인하고 재시도 없이 바로 실패하기 때문이다.

### 관찰
- **낙관적 락 + 재시도의 실패는 규모에 따라 사라진다.** 이 방식에서 실제로 같은 로우를 두고 충돌하는 트랜잭션 수는 HikariCP 풀 크기(예: 10)로 물리적 상한이 걸려 있다 — 지원자가 1,000명으로 늘어나도, 동시에 커넥션을 쥐고 임계 구역(이벤트 조회 → 참여 확인 → 카운터 증가 → 주문/참여 저장)에 들어가 있는 트랜잭션은 여전히 최대 10개뿐이다. 정원(100명)에 비해 지원자(1,000명)가 압도적으로 많으면, 늦게 커넥션을 얻는 대다수는 이미 정원이 찬 상태를 보고 재시도 없이 곧바로, 정상적으로 `PARTICIPANT_LIMIT_EXCEEDED`로 빠진다. 반대로 정원과 지원자 규모가 비슷할 때(시나리오 1)는 대부분의 요청이 "실제로 슬롯을 놓고 경쟁하는" 입장이 되어 동시 충돌이 오래 지속되고, 5회·최대 약 1.25초의 고정 재시도 예산으로는 이 경쟁을 다 흡수하지 못한다 — 즉 이 방식은 "경쟁자 대 슬롯 비율이 1:1에 가까울 때" 가장 취약하고, 수요가 슬롯을 압도할수록 오히려 안정된다.
- **Redis 분산 락의 실패는 규모가 커질수록 그대로, 또는 더 나빠진다.** 이 방식은 슬롯 대비 경쟁률과 무관하게 "이벤트당 락 하나"로 지원자 전원을 한 줄로 세운다. 지원자가 늘어나면 줄이 길어질 뿐, 참여 처리 1건에 걸리는 시간은 거의 일정하므로 `tryLock`의 고정 대기 예산(200ms) 안에 자기 차례가 오는 인원수도 거의 일정하다 — 시나리오 1(20~29명 성공)과 시나리오 2(2~9명 성공)를 비교하면, 지원자가 10배 늘었는데 절대 성공 인원은 오히려 더 줄었다. 즉 이 구조는 "수요가 몰릴수록 더 위험해지는" 설계다 — 정확히 선착순 이벤트가 실제로 가장 많이 겪는 상황에서 가장 취약하다.
- HikariCP `maximum-pool-size`를 10에서 20으로 늘려도 두 방식 모두 개선되지 않는다. 낙관적 락+재시도는 오히려 동시 경쟁자가 늘어 데드락·버전 충돌이 더 잦아져 시나리오 1의 성공률이 더 낮아졌다(48~54 → 31~36). Redis 분산 락은 애초에 병목이 DB 커넥션이 아니라 락 대기 시간이라 풀 크기와 무관하게 거의 그대로다. 커넥션 풀 크기는 이 결함의 근본 원인이 아니다.
- 두 접근 모두 "동시 접근을 어떻게 순서대로 처리할지"를 고정된 시간 예산(재시도 횟수, 락 대기 시간)으로 해결하려 한다는 공통점이 있다. 낙관적 락+재시도는 그 예산이 "물리적으로 상한이 걸린 경쟁 규모"만 흡수하면 되므로 견딜만하지만, Redis 분산 락은 그 예산이 "전체 지원자 수"를 감당해야 해서 수요가 늘어날수록 무너진다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
