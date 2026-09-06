# [Bug] docker-compose 앱 컨테이너에 TOSS_SECRET_KEY 환경변수가 전달되지 않음

### 증상
`docker-compose.yml`로 앱 컨테이너를 기동하면, `.env`에 `TOSS_SECRET_KEY`가 정의돼 있음에도 애플리케이션이 시작 단계에서 해당 값을 찾지 못해 컨테이너가 부팅 실패로 종료된다.

### 환경
- `docker-compose.yml` (`app` 서비스의 `environment` 블록)
- `.env` (`TOSS_SECRET_KEY` 값 정의)
- `src/main/resources/application.yaml` (`toss.secret-key: ${TOSS_SECRET_KEY}`)

### 재현 절차
1. `docker compose up -d mysql-master mysql-slave` 로 DB 컨테이너 기동
2. `docker compose up -d app` 로 앱 컨테이너 기동
3. `docker logs evo-app` 확인

### 관찰
`docker-compose.yml`의 `app.environment` 블록에는 `DB_MASTER_URL`, `DB_SLAVE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`만 정의돼 있고 `TOSS_SECRET_KEY`는 빠져 있다. `application.yaml`은 `toss.secret-key: ${TOSS_SECRET_KEY}`로 이 값을 필수로 요구하므로, 컨테이너 내부에는 `TOSS_SECRET_KEY` 환경변수 자체가 존재하지 않아 플레이스홀더를 해석하지 못하고 빈(Bean) 생성 단계에서 예외가 발생하며 애플리케이션이 종료된다. `.env` 파일에는 값이 정상적으로 있으므로, `docker-compose.yml`에 이 값을 컨테이너로 넘기는 배선이 누락된 것이 문제다.

### 상태
`[CLOSED]`

### 원인 분석
`docker-compose.yml`의 `app.environment` 블록을 작성할 때 `JWT_SECRET`은 `${JWT_SECRET}`으로 `.env`에서 값을 끌어오도록 배선했지만, 같은 시점에 함께 추가됐어야 할 `TOSS_SECRET_KEY`는 이 블록에 추가되지 않은 채 누락됐다. `.env` 파일 자체에는 값이 있어 로컬에서 `./gradlew bootRun`으로 직접 실행할 때는(환경에 따라 `.env`를 셸에 직접 로드해 쓰는 경우) 드러나지 않다가, `docker compose`로 컨테이너를 띄우는 경로에서만 재현됐다.

### 해결 방안
`docker-compose.yml`의 `app.environment` 블록에 `TOSS_SECRET_KEY: ${TOSS_SECRET_KEY}` 한 줄을 추가해 `.env`의 값이 컨테이너로 전달되도록 배선했다.

```yaml
environment:
  DB_MASTER_URL: jdbc:mysql://mysql-master:3306/evo_commerce
  DB_SLAVE_URL: jdbc:mysql://mysql-slave:3306/evo_commerce
  DB_USERNAME: root
  DB_PASSWORD: root1234
  JWT_SECRET: ${JWT_SECRET}
  TOSS_SECRET_KEY: ${TOSS_SECRET_KEY}
```

수정 후 `docker compose up -d app`으로 재기동하면 애플리케이션이 정상 부팅되는 것을 확인했다.
