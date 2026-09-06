# [Bug] 복제 지연 점검 스크립트가 쓰기 부하 없이 측정되어 실제 위험을 놓침

### 증상
`scripts/loadtest/step3_3_check_replication_lag.sh`로 "대량 트래픽 상황"을 점검한 결과, Master-Slave 복제 지연(`Seconds_Behind_Master`)이 항상 `0`으로 측정되어 Step 3.2의 Read/Write 라우팅 적용만으로 복제 지연 문제가 해결된 것처럼 보인다. 그러나 이 측정치는 실제 트래픽 조건을 반영하지 못한 결과다.

### 환경
- `scripts/loadtest/step3_3_check_replication_lag.sh`
- `docker-compose.yml` (`mysql-master`, `mysql-slave` 컨테이너, GTID 기반 복제)
- `src/main/java/com/evo/commerce/global/config/RoutingDataSource.java` (Step 3.2에서 구현된 Read/Write 라우팅)

### 재현 절차
1. `docker compose up -d mysql-master mysql-slave app` 로 전체 스택 기동
2. 회원가입 → 로그인으로 JWT 토큰 발급, 상품 목록 조회용 상품 몇 건 등록
3. `bash scripts/loadtest/step3_3_check_replication_lag.sh {JWT_TOKEN}` 실행 — 내부적으로 `ab -n 2000 -c 1 ... /api/products`를 실행한 뒤, 종료 직후 `SHOW SLAVE STATUS\G`를 한 번만 조회한다
4. 출력된 `Seconds_Behind_Master` 값 확인

### 관찰
실제 실행 결과:
```
Requests per second:    447.41 [#/sec] (mean)
Time per request:       2.235 [ms] (mean)
Seconds_Behind_Master: 0
```
2,000건의 조회 요청을 보냈음에도 `Seconds_Behind_Master`는 한 번도 `0`을 벗어나지 않았다. 이 결과만 보면 "Slave 라우팅 도입으로 복제 지연 리스크가 해소됐다"고 결론 내리기 쉽다.

하지만 같은 환경에서 조회 트래픽과 함께 Master에 쓰기 트래픽(`ab -n 3000 -c 80`으로 상품 등록 API를 동시 호출)을 걸고, `SHOW SLAVE STATUS\G`를 0.2초 간격으로 계속 폴링해보면 다른 결과가 나온다.
```
21:00:44.3xx lag=1
21:00:45.3xx lag=1
21:00:46.3xx lag=1
21:00:47.3xx lag=1
21:00:48.3xx lag=1
21:00:49.3xx lag=1
```
약 5초 동안 `Seconds_Behind_Master`가 `1`로 유지되는 구간이 실측됐다. 두 실험의 차이는 조회 요청 수가 아니라 **Master에 실제로 가해진 쓰기 처리량**이다. `ab -c 1`은 이름과 달리 커넥션을 1개만 사용해 요청을 순차적으로 하나씩 보내므로 "대량 동시 트래픽"을 전혀 재현하지 못하고, 이 조회는 애초에 Slave로 라우팅되어 Master에는 아무 부하도 주지 않는다. 복제 지연은 Slave가 받는 조회량이 아니라 Master의 binlog 기록/Slave의 relay log 적용 속도 차이에서 발생하므로, 쓰기 트래픽이 없는 상태에서 조회만 아무리 반복해도 지연은 절대 관측되지 않는다.

타임세일 이벤트(Step 3.4에서 구현 예정) 상황에서는 선착순 100명의 참여/주문 생성이 짧은 시간에 Master에 집중되는 동시에, 이벤트 조회 트래픽이 Slave에 몰린다 — 이 스크립트가 검증하지 못한 바로 그 조합이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
