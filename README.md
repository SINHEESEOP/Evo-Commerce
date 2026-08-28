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

| ID | 카테고리 | 문제 및 해결 요약 (링크)                                                                                               | 상태 |
|:---|:---|:-----------------------------------------------------------------------------------------------------------------------|:---:|
| **ISSUE-01** | Infra / Docker | [MySQL 컨테이너 기동 지연으로 인한 앱 커넥션 실패](issues/ISSUE-01_mysql_startup_race_condition.md)                    | `CLOSED` |
| **ISSUE-02** | Backend / MVC | [전역 예외 처리기가 서버 오류에도 HTTP 200을 반환](issues/ISSUE-02_exception_handler_returns_200_for_server_errors.md) | `CLOSED` |
| **ISSUE-03** | Testing / Spring | [WebMvcTest의 테스트 내부 정적 컨트롤러 미인식 문제](issues/ISSUE-03_webmvctest_ignores_nested_static_controller.md)   | `CLOSED` |
| **ISSUE-04** | Backend / API Docs | [Swagger UI와 OpenAPI 문서가 환경 구분 없이 항상 노출됨](issues/ISSUE-04_swagger_ui_exposed_without_environment_restriction.md) | `CLOSED` |
| **ISSUE-05** | Backend / JPA | [User 엔티티에 붙인 @Data가 비밀번호 로그 노출과 컬렉션 유실을 동시에 유발](issues/ISSUE-05_user_entity_tostring_leaks_password.md) | `CLOSED` |

> 발생한 이슈는 `issues/ISSUE-{번호}_{버그명}.md`에 기록되며, 해결 완료 시 이 표가 갱신됩니다.

### 기술적 작은 의사결정

| 문서                                                         | 문제 상황 → 선택 → 결과                                                                                                                            |
|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| [001](docs/decisions/001_exception_handler_test_strategy.md) | 예외 처리기 검증에 상태 코드까지 필요해 단위 테스트 대신 슬라이스 테스트를 선택했고, <br/>그 과정에서 만난 WebMvcTest 트러블슈팅을 별도 이슈로 분리했다 |
| [002](docs/decisions/002_error_code_status_mapping.md)       | 비즈니스 예외의 HTTP 상태 코드를 핸들러에서 타입별로 분기하는 대신, <br/>`ErrorCode`가 상태 코드를 스스로 갖게 해 예외 추가 시 핸들러 수정을 없앴다 |
| [003](docs/decisions/003_swagger_exposure_profile_control.md) | Swagger 문서가 환경 구분 없이 항상 열려 있어, `@Profile`로 빈을 막는 대신 <br/>`application.yaml`의 `prod` 프로필 블록에서 springdoc의 `enabled` 스위치를 껐다 |
| [004](docs/decisions/004_user_entity_equals_hashcode.md) | `@Data`가 만든 equals/hashCode가 저장 전후로 값이 바뀌어 HashSet이 엔티티를 잃어버려서, <br/>`hashCode`를 클래스 단위 상수로 고정하고 `equals`는 `id` 기반으로 직접 재정의했다 |

> 구현 중 있었던 작은 기술적 판단을 짧게 기록합니다. 더 큰 아키텍처 결정은 별도로 정리됩니다.
