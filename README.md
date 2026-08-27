# Evo-Commerce

대규모 트래픽과 동시성 이슈를 직접 겪고 해결하는 과정을 기록하는 이커머스 백엔드 포트폴리오 프로젝트입니다.

## 기술 스택

- Java 21 (LTS)
- Spring Boot 3.5.3
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
| ISSUE-01 | Infra/Docker | MySQL 컨테이너 준비 상태 미확인으로 인한 앱 커넥션 실패 | CLOSED | [링크](issues/ISSUE-01_mysql_startup_race_condition.md) |
| ISSUE-02 | Backend/Exception Handling | 전역 예외 처리기가 서버 오류에도 HTTP 200을 응답 | OPEN | [링크](issues/ISSUE-02_exception_handler_returns_200_for_server_errors.md) |
| ISSUE-03 | Testing/Spring | WebMvcTest가 테스트 클래스 내부 정적 클래스를 컨트롤러 빈으로 인식하지 못함 | CLOSED | [링크](issues/ISSUE-03_webmvctest_ignores_nested_static_controller.md) |

> 발생한 이슈는 `issues/ISSUE-{번호}_{버그명}.md`에 기록되며, 해결 완료 시 이 표가 갱신됩니다.

## 기술적 의사결정

| 문서 | 문제 상황 → 선택 → 결과 |
|------|--------------------------|
| [001](docs/decisions/001_exception_handler_test_strategy.md) | 예외 처리기 검증에 상태 코드까지 필요해 단위 테스트 대신 슬라이스 테스트를 선택했고, 그 과정에서 만난 WebMvcTest 트러블슈팅을 별도 이슈로 분리했다 |

> 기술적으로 유의미한 트레이드오프가 있었던 결정만 `docs/decisions/{번호}_{키워드}.md`로 기록됩니다.
