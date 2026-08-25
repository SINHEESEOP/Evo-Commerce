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
`[OPEN]`

### 원인 분석
(해결 완료 후 작성 예정)

### 해결 방안
(해결 완료 후 작성 예정)
