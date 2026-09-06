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
`[CLOSED]`

### 원인 분석
`JpaTransactionManager`의 트랜잭션 시작 절차는 `doBegin(transaction, definition)`을 먼저 실행하고, 그 다음에 `prepareSynchronization(status, definition)`을 실행한다. 실제 JDBC 커넥션 획득은 `doBegin()` 단계에서 일어나지만, `TransactionSynchronizationManager.setCurrentTransactionReadOnly(...)`로 `readOnly` 플래그를 기록하는 일은 그 다음인 `prepareSynchronization()` 단계에서 일어난다.

`DataSourceConfig.dataSource(...)`가 `RoutingDataSource`를 감싸지 않고 그대로 반환하고 있었으므로, `doBegin()`이 커넥션을 요청하는 순간 `RoutingDataSource.determineCurrentLookupKey()`가 즉시 호출됐고, 이 시점에는 아직 `readOnly` 플래그가 세팅되기 전이라 항상 기본값(`false`)을 보고 `DataSourceType.MASTER`를 반환했다. `@Transactional(readOnly = true)`를 정확히 붙여도 결과가 달라지지 않았던 이유다.

이 결함은 예외를 던지지 않고 조용히 실패했다. 라우팅 키 결정 로직만 검증한 단위 테스트(`RoutingDataSourceTest`)는 `TransactionSynchronizationManager.setCurrentTransactionReadOnly(true)`를 테스트 코드가 직접 미리 호출해두고 시작하기 때문에, 실제 트랜잭션 매니저의 "커넥션을 먼저 얻고 플래그는 나중에 세팅하는" 순서 문제를 전혀 재현하지 못해 통과해버렸다.

### 해결 방안
`DataSourceConfig.dataSource(...)`가 최종적으로 반환하는 빈을 `LazyConnectionDataSourceProxy`(실제 커넥션 획득을 그 커넥션이 처음 쓰이는 순간까지 미뤄주는 스프링 제공 프록시)로 감쌌다.

```java
RoutingDataSource routingDataSource = new RoutingDataSource();
// ... setTargetDataSources / setDefaultTargetDataSource ...
routingDataSource.afterPropertiesSet();       // 더 이상 직접 반환되는 빈이 아니므로 수동 호출 필요
return new LazyConnectionDataSourceProxy(routingDataSource);
```

이렇게 하면 `doBegin()` 단계에서는 프록시 객체만 반환되고, 실제 `RoutingDataSource.getConnection()` 호출(따라서 `determineCurrentLookupKey()` 평가)은 `prepareSynchronization()`이 끝난 뒤 첫 SQL이 실행되는 시점까지 미뤄진다. 그 시점에는 `readOnly` 플래그가 이미 올바르게 세팅되어 있어 라우팅이 의도대로 동작한다.

`RoutingDataSource`를 더 이상 빈으로 직접 반환하지 않으므로, 스프링이 자동으로 호출해주던 `afterPropertiesSet()`(`targetDataSources`를 실제 조회 가능한 형태로 초기화하는 콜백)도 더 이상 자동으로 불리지 않는다 — 그대로 두면 `determineTargetDataSource()`가 "DataSource router not initialized" 예외를 던지므로, `afterPropertiesSet()`을 직접 호출하도록 추가했다.

검증을 위해 라우팅 키 로직만 보는 기존 단위 테스트(`RoutingDataSourceTest`)에 더해, 실제 두 MySQL 컨테이너에 대고 `SELECT @@read_only`를 실행해 어느 쪽에 연결됐는지 확인하는 통합 테스트(`RoutingDataSourceIntegrationTest`)를 추가했다. 수정 전에는 이 통합 테스트가 실패했고(읽기 전용 트랜잭션에서도 `@@read_only = 0`), 수정 후에는 통과한다(`@@read_only = 1`). `curl`로 직접 호출해 두 컨테이너의 `general_log`를 비교한 결과도 동일하게 확인했다 — 수정 전에는 조회 쿼리가 Master log에만, 수정 후에는 Slave log에만 찍힌다.
