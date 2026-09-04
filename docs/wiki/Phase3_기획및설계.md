# 타임세일 도메인 설계와 Master-Slave 인프라 확장 전략

## 개요
선착순 100명 한정 타임세일 이벤트 도메인의 ERD와 참여 제한 로직, 그리고 트래픽 증가에 대응하기 위한 MySQL Master-Slave 복제 구성과 Read/Write 라우팅 전략을 정리한다. 타임세일 대상 상품은 기존 `Product` 중 하나를 지정하는 방식으로 설계하고, 결제 이후의 비동기 메시징/이벤트 발행 방식은 이 문서에서 다루지 않는다.

## ERD

```mermaid
erDiagram
    PRODUCTS ||--o{ TIME_SALE_EVENTS : targets
    TIME_SALE_EVENTS ||--o{ TIME_SALE_PARTICIPATIONS : has
    USERS ||--o{ TIME_SALE_PARTICIPATIONS : joins
    ORDERS ||--o| TIME_SALE_PARTICIPATIONS : results_in

    TIME_SALE_EVENTS {
        BIGINT id PK
        BIGINT product_id FK
        INT discount_price
        INT participant_limit
        DATETIME start_at
        DATETIME end_at
        DATETIME created_at
    }

    TIME_SALE_PARTICIPATIONS {
        BIGINT id PK
        BIGINT time_sale_event_id FK
        BIGINT user_id FK
        BIGINT order_id FK
        DATETIME created_at
    }
```

## TimeSaleEvent 도메인 설계

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK, AUTO_INCREMENT) | |
| product_id | BIGINT (FK → products.id) | 타임세일 대상 상품 |
| discount_price | INT | 타임세일 적용가(정가보다 낮은 값) |
| participant_limit | INT | 선착순 참여 가능 인원, 100 고정 |
| start_at / end_at | DATETIME | 이벤트 시작/종료 시각 |
| created_at | DATETIME | |

## TimeSaleParticipation 도메인 설계

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT (PK, AUTO_INCREMENT) | |
| time_sale_event_id | BIGINT (FK → time_sale_events.id) | |
| user_id | BIGINT (FK → users.id) | |
| order_id | BIGINT (FK → orders.id) | 참여 성공 시 함께 생성되는 주문 |
| created_at | DATETIME | |

`(time_sale_event_id, user_id)` 조합에 유니크 제약을 걸어, 같은 사용자가 같은 이벤트에 두 번 참여해 슬롯을 중복으로 차지하는 것은 막는다.

## 선착순 100명 참여 제한 로직

참여 가능 인원은 이벤트당 100명으로 고정한다. 사용자가 "참여하기"를 요청하면 서비스 계층은 현재까지의 참여자 수를 확인하고, `participant_limit`(100) 미만일 때만 `Order`를 생성하고 `TimeSaleParticipation` 레코드를 저장한다.

> [!WARNING]
> "참여자 수 확인"과 "참여 레코드 저장"이 하나의 트랜잭션 안에서는 순서대로 실행되더라도, 서로 다른 두 트랜잭션이 동시에 이 로직을 실행하는 것을 막아주지는 않는다. 참여자 수가 99명인 순간 여러 요청이 동시에 도착하면, 각 트랜잭션이 확인 시점에는 모두 100 미만으로 판단해 전부 저장을 진행할 수 있다 — 확인한 시점과 그 값을 실제로 사용하는 시점 사이에 다른 트랜잭션이 끼어드는 경쟁 조건(TOCTOU)이다.

이 경쟁 조건을 막을 구체적인 방법(비관적 락, 낙관적 락 + 재시도, Redis 분산 락 등)은 실제 동시 요청으로 재현한 뒤 트레이드오프를 비교해 별도 단계에서 결정하며, 이 문서에서는 다루지 않는다.

## Master-Slave 복제 구성과 Read/Write 라우팅

```mermaid
flowchart LR
    App[Spring Boot App] -->|write| Master[(MySQL Master)]
    App -->|read| Slave[(MySQL Slave)]
    Master -->|binlog replication| Slave
```

타임세일 시작 직후 짧은 시간에 상품/이벤트 조회 트래픽이 집중되므로, 조회 트래픽을 Master에서 분리한다. `AbstractRoutingDataSource`를 이용해 트랜잭션의 읽기 전용 여부(`@Transactional(readOnly = true)`)를 기준으로 라우팅 대상을 결정한다.

- 쓰기 트랜잭션(`@Transactional`) → Master로 라우팅
- 읽기 전용 트랜잭션(`@Transactional(readOnly = true)`) → Slave로 라우팅

이벤트 목록/상세 조회, 상품 목록 조회처럼 트래픽이 집중되는 API는 모두 `readOnly = true`로 선언되어 있으므로, 이 규칙 하나로 조회 트래픽 대부분이 Slave로 분산된다.

> [!IMPORTANT]
> Master에 커밋된 변경 사항이 Slave에 반영되기까지는 복제 지연(Replication Lag)이 존재한다. 참여 직후 자기 자신의 참여 내역이나 주문 상세를 조회하는 화면은, 방금 Master에 쓴 데이터를 곧바로 다시 읽어야 하는 read-after-write 시나리오라 이 규칙을 그대로 적용하면 "방금 성공했는데 조회하면 안 보인다"는 정합성 문제가 노출된다.
>
> 따라서 참여 직후 본인의 참여 내역/주문 상세를 조회하는 API처럼 read-after-write이 요구되는 경로는 읽기 전용 트랜잭션이더라도 예외적으로 Master로 라우팅한다. 그 외 이벤트 목록/상품 목록처럼 방금 쓴 데이터를 곧바로 다시 읽을 필요가 없는 조회는 기존 규칙대로 Slave로 라우팅한다.

## 이 문서에서 다루지 않는 범위

- 참여/주문 생성 이후 결제 완료 이벤트를 메시지 큐로 안정적으로 발행하는 방식(Outbox 패턴)은 별도 설계에서 다룬다.
- Redis 분산 락 등 애플리케이션 레벨 동시성 제어 구현 세부사항은 실제 부하 재현 이후 별도로 다룬다.
