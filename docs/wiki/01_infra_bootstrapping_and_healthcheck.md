# Docker Compose 콜드 스타트 레이스 컨디션과 헬스체크 기반 기동 순서 제어

## 문제 상황

`docker-compose up -d`로 MySQL과 애플리케이션 컨테이너를 함께 최초 기동(cold start)하면, `evo-app` 컨테이너가 다음과 같은 로그를 남기고 종료되거나 재시작을 반복했다.

```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
Caused by: java.net.ConnectException: Connection refused
```

같은 명령을 한 번 더 실행하면(즉 MySQL 컨테이너가 이미 떠 있는 상태에서 앱만 재기동하면) 정상적으로 연결됐다. 매번 실패하는 게 아니라 최초 cold start 시점에서만 간헐적으로 재현되는 비결정적(non-deterministic) 문제였다는 점이 원인 파악을 어렵게 만들었다.

## 원인 분석

`docker-compose.yml`의 `app` 서비스는 처음에 `depends_on`을 조건 없는 리스트 형태로 선언하고 있었다.

```yaml
app:
  depends_on:
    - mysql
```

Docker Compose에서 이 형태의 `depends_on`은 **컨테이너 프로세스의 시작 순서만** 보장한다. "프로세스가 시작됐다"는 것과 "그 프로세스가 실제로 요청을 처리할 준비가 됐다"는 것은 서로 다른 시점이다. MySQL은 컨테이너 프로세스가 뜬 이후에도 InnoDB 초기화 등 부트스트랩 과정을 거치며, 이 시간 동안은 3306 포트가 아직 커넥션을 받지 않는다.

반면 Spring Boot의 HikariCP는 애플리케이션 컨텍스트 초기화 시점에 즉시 DB 커넥션을 시도한다. 두 컨테이너가 동시에 시작되더라도 MySQL의 부트스트랩이 끝나기 전에 앱이 먼저 연결을 시도하면 `Connection refused`가 발생하는 구조였다. 로컬 환경의 디스크/CPU 성능에 따라 부트스트랩 소요 시간이 달라지므로, 매번 재현되지는 않고 간헐적으로만 실패하는 형태로 나타났다.

## 검토한 대안

**대안 1: 애플리케이션 레벨 재시도**
HikariCP의 `initializationFailTimeout`이나 자체 재시도 로직으로 앱이 DB 연결 실패를 견디게 만드는 방법. 코드/설정 변경만으로 해결할 수 있다는 장점이 있지만, 재시도 횟수·간격을 잘못 설정하면 컨테이너 기동 자체가 실패 처리될 위험이 있다. 근본적으로도 "인프라 준비 상태를 보장하는 문제"를 애플리케이션 책임으로 떠넘기는 접근이라 기각했다.

**대안 2 (채택): 헬스체크 기반 기동 순서 제어**
MySQL 서비스에 헬스체크를 추가하고, 앱의 `depends_on`을 `condition: service_healthy`로 지정해 인프라 레벨에서 "실제로 요청을 받을 수 있는 상태"를 명시적으로 검증하는 방법. 재현 조건 자체를 제거할 수 있다는 점에서 채택했다.

## 적용한 해결책

```yaml
mysql:
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot1234"]
    interval: 5s
    timeout: 5s
    retries: 10

app:
  depends_on:
    mysql:
      condition: service_healthy
```

`mysqladmin ping`은 MySQL 서버가 실제로 클라이언트 요청을 받을 수 있는 상태인지 확인하는 명령이다. 이 헬스체크가 통과해야 `service_healthy` 상태가 되고, 그 이후에야 `app` 컨테이너가 기동을 시작한다. `interval`/`timeout`/`retries` 값은 로컬 개발 환경의 기동 시간을 기준으로 잡았다.

Redis와 RabbitMQ는 인증/커넥션 풀 초기화 과정이 MySQL만큼 무겁지 않아 별도 헬스체크 없이 `condition: service_started`만으로 충분하다고 판단하고, 실제로 그 서비스들을 소비하는 시점에 맞춰 나중에 추가하기로 했다.

## 결과

`docker-compose down -v`로 전체 스택을 완전히 제거한 뒤 다시 `docker-compose up -d`로 cold start를 여러 차례 반복해도 `Connection refused`가 재현되지 않았다. 이후 JPA 연동과 실제 커넥션 로직이 추가된 뒤에도 동일한 기동 순서 보장이 유지됐다.

## 일반화할 수 있는 점

이 문제의 본질은 "프로세스 시작(Started)"과 "서비스 준비 완료(Ready)"를 같은 시점으로 취급한 것이었다. `depends_on`의 조건 없는 리스트 형태는 전자만 보장하는데, 실제로 필요했던 건 후자였다. 컨테이너 오케스트레이션에서 두 상태를 구분하지 않으면 이번처럼 로컬 환경의 성능 차이에 따라 간헐적으로만 재현되는, 디버깅하기 까다로운 비결정적 버그로 이어지기 쉽다. 헬스체크는 이 구분을 인프라 레벨에서 명시적으로 표현하는 수단이었다.

남은 과제로는, 지금 설정된 `interval`/`retries` 값이 로컬 개발 환경 기준이라는 점이다. CI나 다른 인프라 환경에서 MySQL 부트스트랩 시간이 달라지면 값 재조정이 필요할 수 있다.
