# [Bug] 읽기 전용 트랜잭션이 Slave가 아닌 Master로 라우팅됨

### 증상
`@Transactional(readOnly = true)`로 선언된 조회 요청(예: 상품 목록 조회)을 호출해도, 실제 쿼리는 매번 Master DB로만 전달된다. 애플리케이션 로그에는 예외나 에러가 전혀 남지 않고, HTTP 응답도 정상(200)으로 내려온다.

### 환경
- `src/main/java/com/evo/commerce/global/config/DataSourceConfig.java`
- `src/main/java/com/evo/commerce/global/config/RoutingDataSource.java`
- `docker-compose.yml` (`mysql-master`, `mysql-slave` 컨테이너)
- MySQL 8.0 Master-Slave 복제 구성 (GTID 기반)

### 재현 절차
1. `docker compose down -v` (기존 볼륨까지 전체 초기화)
2. `docker compose up -d mysql-master mysql-slave` 후 `docker exec evo-mysql-slave mysql -uroot -proot1234 -e "SHOW SLAVE STATUS\G"`로 `Slave_IO_Running: Yes`, `Slave_SQL_Running: Yes` 확인 (복제 자체는 정상 동작)
3. 두 컨테이너 모두에서 `SET GLOBAL general_log='ON'; SET GLOBAL general_log_file='/var/lib/mysql/general.log';` 실행
4. `./gradlew bootRun`으로 앱 기동
5. 회원가입 → 로그인으로 JWT 토큰 발급
6. `curl http://localhost:8080/api/products -H "Authorization: Bearer {토큰}"` 호출 (내부적으로 `ProductRepository.findAll()`을 호출하며, `SimpleJpaRepository`가 기본으로 갖는 `@Transactional(readOnly = true)` 트랜잭션 안에서 실행됨)
7. 두 컨테이너의 `/var/lib/mysql/general.log`에서 `select ... from products` 쿼리가 어느 쪽에 찍혔는지 확인

### 관찰
6번 요청의 `select p1_0.id,... from products p1_0` 쿼리가 **Master(`evo-mysql-master`)의 general log에만** 찍히고, Slave(`evo-mysql-slave`)의 general log에는 전혀 나타나지 않는다. `RoutingDataSource.determineCurrentLookupKey()`는 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()` 값을 그대로 반환하도록 구현되어 있고, 이 로직만 따로 단위 테스트(`RoutingDataSourceTest`)로 검증했을 때는 `readOnly = true`를 주면 `DataSourceType.SLAVE`를 정확히 반환해 테스트가 통과한다. 그런데도 실제 요청에서는 매번 Master로만 쿼리가 전달된다 — 즉 라우팅 키를 결정하는 로직 자체는 맞지만, 실제 커넥션 획득 시점에는 그 로직이 반환한 값이 반영되지 않고 있다는 뜻이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
