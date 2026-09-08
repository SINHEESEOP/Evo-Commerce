# [Bug] 타임세일 참여 API가 이벤트 시작/종료 시각을 검증하지 않음

### 증상
- 타임세일 이벤트의 `startAt` 이전, 또는 `endAt` 이후 시각에 `POST /api/timesales/{eventId}/participate`를 직접 호출해도 200 OK와 함께 참여(주문 생성)가 정상 처리된다.
- 클라이언트 화면(`timesale-detail.html`)에서는 카운트다운이 끝나기 전/후에는 "참여하기" 버튼이 비활성화되어 있어 정상적인 사용 흐름에서는 증상이 드러나지 않는다.

### 환경
- `src/main/java/com/evo/commerce/domain/timesale/application/TimeSaleFacade.java` (`participate` 메서드)
- `src/main/resources/static/timesale-detail.html` (프런트엔드 카운트다운/버튼 활성화 로직)

### 재현 절차
1. MASTER 계정으로 로그인 후 `POST /api/timesales`로 `startAt`을 미래 시각(예: 1시간 뒤)으로 하는 타임세일 이벤트를 등록한다.
2. 발급받은 JWT로 브라우저 화면을 거치지 않고 곧바로 `POST /api/timesales/{eventId}/participate`를 호출한다.
   ```
   curl -X POST http://localhost:8080/api/timesales/1/participate \
     -H "Authorization: Bearer {ACCESS_TOKEN}"
   ```
3. 이벤트 시작 전임에도 `ApiResponse.success(...)`와 함께 참여가 성공하고, 주문이 생성됨을 확인한다.
4. 같은 방식으로 `endAt`을 과거 시각으로 등록한 이벤트에 대해서도 참여 요청이 성공함을 확인한다.

### 관찰
- `timesale-detail.html`의 카운트다운은 `now < startAt` / `now < endAt` 조건으로 "참여하기" 버튼의 `disabled` 속성만 제어할 뿐, 이 판단은 순수 클라이언트 로직이다.
- `TimeSaleFacade.participate(Long userId, Long eventId)`는 이벤트 존재 여부, 중복 참여 여부, 선착순 인원 초과 여부만 검사하고, `event.getStartAt()` / `event.getEndAt()`과 현재 시각을 비교하는 로직이 없다.
- 즉 "타임세일 진행 중에만 참여 가능하다"는 핵심 비즈니스 규칙이 서버가 아닌 브라우저 UI에서만 강제되고 있어, API를 직접 호출하는 경로에서는 그대로 우회된다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성)

### 해결 방안
(해결 시 작성)
