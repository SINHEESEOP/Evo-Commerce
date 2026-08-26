# [Bug] MySQL 컨테이너 준비 상태 미확인으로 인한 앱 커넥션 실패

### 증상
- `docker-compose up -d` 로 전체 스택을 최초 기동(cold start)하면, `evo-app` 컨테이너가 아래와 유사한 로그를 남기고 종료되거나 재시작을 반복한다.
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
Caused by: java.net.ConnectException: Connection refused
```
- 동일한 `docker-compose up -d` 명령을 다시 한 번 더 실행하면(즉, mysql 컨테이너가 이미 떠 있는 상태에서 app만 재기동하면) 정상적으로 연결된다.
- 즉, 매번 실패하는 것이 아니라 **최초 cold start 시점에서만** 간헐적으로 재현되는 비결정적(non-deterministic) 문제다.

### 환경
- `docker-compose.yml` (mysql, redis, rabbitmq, app 서비스 정의)
- `Dockerfile` (eclipse-temurin:21-jdk 기반)
- MySQL 8.0 (컨테이너명 `evo-mysql`)
- Spring Boot 3.5.x, `spring-boot-starter` 기본 의존성만 포함된 상태

### 재현 절차
1. 기존 컨테이너/볼륨을 완전히 제거하여 cold start 상태를 만든다.
   ```bash
   docker-compose down -v
   ```
2. 전체 스택을 한 번에 기동한다.
   ```bash
   docker-compose up -d
   ```
3. `evo-app` 컨테이너 로그를 확인한다.
   ```bash
   docker logs -f evo-app
   ```
4. DB 연결 예외 발생 여부를 확인한다. (컴퓨터 사양/디스크 속도에 따라 재현율이 달라질 수 있음)

### 관찰
- `docker-compose.yml`의 `app` 서비스는 `depends_on`으로 `mysql`을 명시하고 있다.
- 그러나 app 컨테이너의 로그 타임스탬프와 mysql 컨테이너의 "ready for connections" 로그 타임스탬프를 비교하면, app이 먼저 DB 커넥션을 시도하고 mysql은 그 이후에 초기화를 완료하는 순서가 관찰된다.
- "컨테이너 프로세스 시작"과 "해당 프로세스의 요청 처리 준비 완료"는 서로 다른 시점이다.

### 상태
`[CLOSED]`

### 원인 분석
- Docker Compose의 `depends_on`(조건 없는 리스트 형태)은 컨테이너 시작 순서만 보장하고, 컨테이너 내부 프로세스의 요청 처리 준비 상태는 보장하지 않는다.
- MySQL 컨테이너는 프로세스 기동 후에도 InnoDB 초기화 등 부트스트랩 과정을 거치며, 이 시간 동안 3306 포트는 아직 커넥션을 받지 않는다.
- Spring Boot의 HikariCP는 애플리케이션 컨텍스트 초기화 시점에 즉시 DB 커넥션을 시도하므로, MySQL 부트스트랩이 끝나기 전에 연결을 시도하면 `Connection refused`가 발생한다.

### 해결 방안
- 대안 1: 애플리케이션 레벨 재시도 (HikariCP `initializationFailTimeout`, 커넥션 재시도 로직) — 코드/설정 변경만으로 해결 가능하지만 재시도 횟수·간격을 잘못 설정하면 컨테이너 기동 자체가 실패로 처리될 수 있고, 인프라 문제를 애플리케이션 책임으로 떠넘기는 방식이다.
- 대안 2 (채택): `mysql` 서비스에 `healthcheck`(`mysqladmin ping`)를 추가하고, `app`의 `depends_on`을 `condition: service_healthy`로 지정. 인프라 레벨에서 "실제로 요청을 받을 수 있는 상태"를 명시적으로 검증하므로 재현 조건 자체를 제거한다.
- 최종 적용: `docker-compose.yml`의 `mysql.healthcheck` 및 `app.depends_on.mysql.condition: service_healthy` 설정.
