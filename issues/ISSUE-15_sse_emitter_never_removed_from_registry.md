# [Bug] SSE 구독이 반복될수록 죽은 Emitter가 레지스트리에 무한정 쌓임

### 증상
- `/api/notifications/subscribe`로 SSE 구독을 여러 번 반복하면(브라우저 재연결, 탭 재오픈 등), 같은 사용자의 `SseEmitterRegistry` 항목 리스트 크기가 구독 횟수만큼 계속 늘어난다.
- 연결이 끊긴(죽은) `SseEmitter`도 리스트에서 전혀 제거되지 않는다.
- 알림 push 시점(`NotificationEventListener.handleOrderPaid()`)에 죽은 emitter까지 순회 대상이 되며, 죽은 emitter에 `send()`를 호출하면 `IOException`이 발생할 수 있다.

### 환경
- `src/main/java/com/evo/commerce/domain/notification/SseEmitterRegistry.java`
- `src/main/java/com/evo/commerce/domain/notification/NotificationController.java`
- `src/main/java/com/evo/commerce/domain/notification/NotificationEventListener.java`

### 재현 절차
1. `SseEmitterRegistryTest`의 `같은_사용자가_여러_번_구독하면_전부_목록에_쌓인다` 테스트대로, 같은 `userId`로 `register()`를 여러 번 호출한다.
2. `findByUserId(userId)`로 조회하면 호출한 횟수만큼 `SseEmitter`가 리스트에 쌓여 있다.
3. 실제 브라우저에서는 `EventSource`가 연결이 끊길 때마다 자동으로 재연결을 시도하므로, `/api/notifications/subscribe`가 반복 호출되고 위 현상이 저절로 재현된다.
4. `register()`는 `emitter.onCompletion()` / `onTimeout()` / `onError()` 콜백을 전혀 등록하지 않으므로, 어떤 종료 상황에서도 리스트에서 자기 자신을 제거하지 않는다.

### 관찰
- `new SseEmitter()`는 타임아웃을 지정하지 않아, 스프링 MVC의 비동기 요청 타임아웃 기본값(환경에 따라 다르지만 보통 분 단위)을 그대로 따른다. 그 시간 동안은 죽은 연결도 "살아있는 것"으로 취급된다.
- 정상 종료/타임아웃/에러 세 가지 콜백 중 어느 것도 등록돼 있지 않아, `emitters` 맵에서 항목을 제거할 방법이 코드상 존재하지 않는다.
- 접속·재접속이 반복되는 환경(모바일 네트워크 전환, 백그라운드 탭 등)에서 장시간 운영하면 `emitters` 맵이 무한정 커지는 메모리 누수로 이어질 수 있다.
- 트래픽이 적을 때는 쌓이는 속도가 느려 당장 장애로 드러나지 않는다 — 실제 부하 테스트나 메모리 프로파일링 없이는 코드 리뷰만으로 발견하기 어렵다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
