# Wiki Deep-Dive 퍼블리싱 가이드

본 문서는 개발 과정에서 누적된 `issues/` 및 `docs/retrospectives/` 를 기반으로, 채용 담당자 및 기술 면접관을 위한 '고품질 기술 아티클(Wiki)'을 발행하는 시점과 주제를 정의한다.

## 1. 운영 원칙
- **개발 중**: 개별 스텝마다 `issues/` 에 팩트 기반의 이슈 티켓을 기록한다.
- **Phase 완료 시**: 해당 Phase에서 해결한 핵심 엔지니어링 문제를 엮어 `docs/wiki/` 에 딥다이브 아티클을 생성한다.
- **포트폴리오 배포 시**: `docs/wiki/` 마크다운 파일들을 GitHub 웹 레포지토리의 **Wiki** 탭으로 일괄 퍼블리싱한다.

---

## 2. Phase별 Wiki 발행 로드맵

| 완료 시점 | Wiki 문서 파일명 | 핵심 주제 및 다룰 내용 |
| :--- | :--- | :--- |
| **Phase 1 종료** | `docs/wiki/01_infra_bootstrapping_and_healthcheck.md` | **[Infra] Docker Compose 콜드 스타트 레이스 컨디션 해결과 컨테이너 헬스체크 최적화**<br>- 프로세스 기동(Started)과 서비스 준비(Ready)의 차이<br>- `mysqladmin ping` 헬스체크 및 `condition: service_healthy` 오케스트레이션 |
| **Phase 2 종료** | `docs/wiki/02_concurrency_control_deep_dive.md` | **[Concurrency] 선착순 재고 차감 동시성 제어 전략 비교 분석**<br>- Naive 구현 시 Race Condition 및 데이터 정합성 깨짐 재현<br>- 비관적 락(Pessimistic Lock) vs 낙관적 락(Optimistic Lock) vs Redis 분산 락(Redisson) 트레이드오프<br>- 락 획득 타임아웃과 트랜잭션 범위 최소화 설계 |
| **Phase 3 종료** | `docs/wiki/03_async_messaging_and_outbox_pattern.md` | **[Messaging] RabbitMQ 기반 비동기 결제 파이프라인과 데이터 일관성 보장**<br>- 동기식 외부 결제 호출 병목 및 장애 전파 문제 해결<br>- Transactional Outbox Pattern + Polling/CDC 조합을 통한 메시지 발행 보장<br>- 멱등성(Idempotency) 보장을 위한 Consumer 설계 |
| **Phase 4 종료** | `docs/wiki/04_caching_strategy_and_stampede_protection.md` | **[Performance] 대규모 상품 조회 캐싱 아키텍처 및 쿼리 최적화**<br>- Cache Aside 패턴 적용과 Redis 메모리 구조 최적화<br>- Cache Stampede 방어를 위한 분산 락 및 Probabilistic Early Expiration(PER)<br>- N+1 문제 해결 및 커버링 인덱스를 활용한 페이징 성능 개선 |
| **Phase 5 종료** | `docs/wiki/05_load_testing_and_performance_tuning.md` | **[Tuning] k6 부하 테스트 기반 병목 추적 및 Java 21 / JVM 성능 튜닝**<br>- VU 증가에 따른 TPS/RPS 병목 지점 프로파일링 (HikariCP 커넥션 풀 고갈 추적)<br>- Java 21 Virtual Thread 적용에 따른 처리량 및 I/O 블로킹 개선 지표 비교<br>- 튜닝 전/후 p95, p99 레이턴시 및 리소스 사용량 종합 비교표 |

---

## 3. GitHub Wiki 퍼블리싱 절차 (수동 10초 완료)
1. GitHub 레포지토리 상단의 **Wiki** 탭 이동 후 `New Page` 클릭
2. `docs/wiki/` 에 생성된 마크다운 본문을 그대로 붙여넣기
3. 사이드바(Sidebar)에 목차 링크를 추가하여 완성