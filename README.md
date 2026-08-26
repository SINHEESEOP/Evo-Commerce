# Evo-Commerce

대규모 트래픽과 동시성 이슈를 직접 겪고 해결하는 과정을 기록하는 이커머스 백엔드 포트폴리오 프로젝트입니다.

## 기술 스택

- Java 21 (LTS)
- Spring Boot 3.5.x
- MySQL 8.0
- Redis 7.2
- RabbitMQ 3.13
- Docker / Docker Compose

## 실행 방법

```bash
docker-compose up -d
./gradlew bootRun
```

## 트러블슈팅

| ID | 카테고리 | 설명 | 상태 | 문서 |
|----|----------|------|------|------|
| ISSUE-01 | Infra/Docker | MySQL 컨테이너 준비 상태 미확인으로 인한 앱 커넥션 실패 | OPEN | [링크](issues/ISSUE-01_mysql_startup_race_condition.md) |

> 발생한 이슈는 `issues/ISSUE-{번호}_{버그명}.md`에 기록되며, 해결 완료 시 이 표가 갱신됩니다.
