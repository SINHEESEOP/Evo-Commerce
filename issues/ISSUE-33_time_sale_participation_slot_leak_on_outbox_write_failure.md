# [Bug] Redis 원자적 참여 확정 후 아웃박스 기록 실패 시 참여 슬롯이 보상 없이 영구 유실됨

### 증상
- 참여 요청 처리 순서는 "① Redis Lua 스크립트로 잔여 수량 확인·중복 확인·차감(원자적) → ② 아웃박스 테이블에 참여 사실 기록"이다. ①이 성공한 직후 ②가 DB 장애(커넥션 풀 고갈, 커넥션 끊김 등)로 실패하면, 요청은 500 에러로 끝나지만 ①에서 소비한 Redis 슬롯은 그대로 남는다.
- 같은 사용자가 재시도하면 Redis의 중복 참여 검사(`SISMEMBER`)에 걸려 `ALREADY_PARTICIPATED`(409)로 거부된다 — 정작 해당 사용자의 `Order`/`TimeSaleParticipation` 레코드는 DB에 하나도 없는데도 다시 참여할 방법이 없다.
- 이벤트의 잔여 수량(Redis)은 영구적으로 1 줄어든 채 복구되지 않는다. 시간이 지날수록 "Redis 기준 마감"과 "실제 완료된 주문 수" 사이의 간극이 누적된다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` — `participate()`, `reserveParticipation()`, `publishParticipationRequested()`
- `src/main/java/com/evo/commerce/domain/order/domain/OutboxEvent.java`, `OutboxEventRepository.java` — 참여 사실을 기록하는 아웃박스 테이블(비동기 처리를 위한 유일한 durability 지점)
- Redis 키: `time-sale:remaining:{eventId}`(잔여 수량), `time-sale:participants:{eventId}`(중복 참여 방지 집합)
- Docker MySQL Master(`evo-mysql-master`), Redis(`evo-redis`) 컨테이너 위에서 재현.

### 재현 절차
1. 진행 중인 `TimeSaleEvent`(정원 5명)와 사용자 1명을 준비한다.
2. `OutboxEventRepository.save(...)`가 예외(`DataAccessResourceFailureException` 등, DB 장애 상황을 흉내)를 던지도록 만든 상태에서 해당 사용자로 `TimeSaleFacade.participate(userId, eventId)`를 1차 호출한다.
   - 결과: 예외가 그대로 호출자에게 전파된다(실제 API라면 500 응답).
3. DB 장애가 복구됐다고 가정하고, 같은 사용자로 `participate(userId, eventId)`를 2차 호출한다.
   - 결과: `BusinessException(ALREADY_PARTICIPATED)`(409)로 거부된다.
4. `TimeSaleParticipationRepository.countByTimeSaleEvent(event)`로 실제 저장된 참여 건수를 확인한다.
   - 결과: `0`건. 이 사용자에 대한 `Order`/`TimeSaleParticipation`은 DB 어디에도 존재하지 않는다.
5. 위 절차를 실제로 실행해 확인한 로그:
   ```
   === 1차 시도 (DB 장애 상황) ===
   예외 발생(예상됨): DataAccessResourceFailureException - DB 커넥션 없음(재현용)
   === 2차 시도 (같은 사용자, DB 복구 가정하고 재시도) ===
   예외 발생(예상됨): BusinessException - 이미 참여한 타임세일입니다.
   === 결과 ===
   실제 저장된 참여(TimeSaleParticipation) 건수: 0
   ```

### 관찰
- **원자성의 경계가 Redis 안쪽에서 끝난다.** Lua 스크립트는 "잔여 수량 확인 + 중복 확인 + 차감"을 Redis 내부에서만 원자적으로 묶어준다. 그 결과를 DB에 반영하는 다음 단계(아웃박스 INSERT)는 Redis 트랜잭션의 바깥이고, 두 시스템을 하나로 묶는 분산 트랜잭션이 없다 — Redis 쪽 성공을 DB 쪽 실패에 맞춰 되돌릴 방법이 코드 어디에도 없다.
- **기존 트랜잭셔널 아웃박스 패턴의 전제가 깨졌다.** Step 3.7/3.8에서 도입한 아웃박스 패턴은 원래 "업무 데이터 저장 + 아웃박스 기록을 같은 로컬 DB 트랜잭션 안에서 원자적으로 처리한다"는 전제로 이중 쓰기(dual write) 문제를 해결했다. 이번 설계는 업무 행위(재고 차감·중복 검증)가 이미 DB 바깥의 Redis에서 끝나버리므로, 아웃박스는 더 이상 "같은 트랜잭션에 묶인 안전한 기록"이 아니라 "Redis 쪽 성공을 DB에 반영하는 유일하고 보상 수단이 없는 마지막 관문"이 된다.
- **ISSUE-32와의 연결고리.** ISSUE-32에서 다룬 "HikariCP 풀 고갈로 인한 오탐 거부"는 이번 설계에서 참여 확정 자체의 거부 원인은 되지 않지만(참여 확정은 Redis 안에서 끝나므로), 그 직후의 아웃박스 INSERT 한 번은 여전히 DB 커넥션을 필요로 한다. 즉 이 결함은 "커넥션 풀이 순간적으로라도 고갈되는 모든 상황"에서 조용히 슬롯을 영구히 잃는 형태로 재발한다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
