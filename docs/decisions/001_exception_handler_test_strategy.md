# 전역 예외 처리기 테스트 전략: 단위 테스트 vs 슬라이스 테스트

### 문제 상황
`GlobalExceptionHandler`가 원하는 동작을 하는지 검증할 테스트가 필요했다. 이 컴포넌트는 `@RestControllerAdvice`라서, 클래스 자체의 로직만 검증하면 되는지 아니면 Spring이 실제 예외 처리 체인에 이 클래스를 끼워 넣어주는 것까지 검증해야 하는지 판단이 필요했다.

### 검토한 대안
- **순수 단위 테스트**: `new GlobalExceptionHandler()`로 직접 인스턴스를 만들어 `handleException(...)`을 호출하고 반환값을 검증한다. Spring 컨텍스트가 필요 없어 빠르고 단순하다. 다만 이 메서드가 실제 HTTP 요청 처리 중 자동으로 호출되는지, 최종적으로 클라이언트가 받는 HTTP 상태 코드가 무엇인지는 검증하지 못한다.
- **`@WebMvcTest` 슬라이스 테스트**: 실제 `DispatcherServlet`을 통해 요청을 흘려보내 최종 HTTP 상태 코드와 응답 바디를 검증한다. Spring 컨텍스트를 띄우는 비용이 있지만, 클라이언트가 실제로 받는 응답을 그대로 검증할 수 있다.

### 결정 및 이유
슬라이스 테스트를 채택했다. 지금 검증하려는 결함(전역 예외 처리기가 서버 오류에도 HTTP 200을 응답하는 문제, [ISSUE-02](../../issues/ISSUE-02_exception_handler_returns_200_for_server_errors.md))의 핵심이 "클라이언트가 실제로 받는 HTTP 상태 코드"이기 때문에, 이 메서드를 직접 호출해서 반환값만 보는 단위 테스트로는 결함 자체를 테스트로 표현할 수 없었다.

### 적용 결과
슬라이스 테스트로 전환하는 과정에서 별도의 실제 문제를 만났다. 테스트 클래스 내부에 중첩시킨 더미 컨트롤러를 `@WebMvcTest`가 컨트롤러 빈으로 인식하지 못해 테스트가 의도와 다른 이유로 실패했다. 이 문제와 원인, 해결 과정은 [ISSUE-03](../../issues/ISSUE-03_webmvctest_ignores_nested_static_controller.md)에 별도로 정리했다. 더미 컨트롤러를 최상위 클래스로 추출한 뒤로는 슬라이스 테스트가 정상 동작했고, 이 테스트는 이후 `GlobalExceptionHandler`를 상태 코드/예외 계층 기준으로 리팩터링할 때 회귀를 잡아주는 안전망 역할을 한다.

### 관련 이슈
- [ISSUE-02](../../issues/ISSUE-02_exception_handler_returns_200_for_server_errors.md) — 이 테스트가 검증하는 결함
- [ISSUE-03](../../issues/ISSUE-03_webmvctest_ignores_nested_static_controller.md) — 테스트 전략을 구현하는 중 만난 별도 트러블슈팅
