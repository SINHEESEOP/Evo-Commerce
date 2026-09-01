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

`.env.example`을 복사해 `.env`를 만들고 값을 채운다(이 파일은 git에 커밋되지 않는다).

```bash
cp .env.example .env
```

```bash
docker-compose up -d
./gradlew bootRun
```

## 이슈 리스트

개발 중 만난 버그 전체 목록은 [Wiki: 이슈 리스트](https://github.com/SINHEESEOP/Evo-Commerce/wiki/이슈-리스트)에서 확인할 수 있습니다.

## 트러블슈팅

구조적인 원인 분석이나 트레이드오프 비교가 필요했던 굵직한 주제(동시성 제어, 비동기 메시징, 캐싱 전략, 부하 테스트 등)는 [GitHub Wiki](https://github.com/SINHEESEOP/Evo-Commerce/wiki)에 딥다이브로 정리됩니다.

## 구현 메모

구현 중 있었던 작은 기술적 판단은 [Wiki: 구현 메모](https://github.com/SINHEESEOP/Evo-Commerce/wiki/구현-메모)에 정리돼 있습니다.
