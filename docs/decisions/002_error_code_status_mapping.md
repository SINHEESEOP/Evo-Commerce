# 비즈니스 예외의 HTTP 상태 코드 결정 방식: ErrorCode 자체 보유 vs 핸들러 매핑

### 문제 상황
`BusinessException`이 발생했을 때 `GlobalExceptionHandler`가 어떤 HTTP 상태 코드로 응답할지 결정해야 했다. 예외 타입을 핸들러에서 분기해서 상태 코드를 정할지, 예외 자체가 상태 코드를 들고 있게 할지 판단이 필요했다.

### 검토한 대안
- **핸들러에서 타입 분기**: `@ExceptionHandler`에서 `instanceof`나 여러 개의 `@ExceptionHandler` 메서드로 구체 예외 타입별 상태 코드를 매핑한다. 예외 타입만 보고도 상태 코드를 알 수 있어 직관적이지만, 새로운 비즈니스 예외가 추가될 때마다 `GlobalExceptionHandler`를 함께 수정해야 한다.
- **`ErrorCode`가 상태 코드를 자체 보유**: `ErrorCode` 인터페이스에 `getHttpStatus()`를 두고, `BusinessException`이 생성 시점에 `ErrorCode`를 받아 갖고 있게 한다. 핸들러는 `BusinessException` 하나만 잡아서 `e.getErrorCode().getHttpStatus()`를 그대로 사용한다.

### 결정 및 이유
`ErrorCode` 자체 보유 방식을 채택했다. 새 비즈니스 예외가 생길 때마다 `GlobalExceptionHandler`를 수정하는 대신, `ErrorCode` 구현체(enum 상수)만 추가하면 되도록 변경 지점을 한 곳으로 좁히기 위해서다. 아직 User/Product 도메인 예외가 없는 시점이라 확장 비용을 낮게 유지하는 쪽이 유리하다고 판단했다.

### 적용 결과
`GlobalExceptionHandler`는 `BusinessException` 하나만 잡는 핸들러 메서드로 단순화됐고, 공통 예외는 `CommonErrorCode` enum(`INVALID_INPUT_VALUE`, `INTERNAL_SERVER_ERROR`)으로 정의했다. 도메인별 예외가 추가될 때는 해당 도메인에 맞는 `ErrorCode` enum만 새로 만들면 되고, 핸들러 코드는 변경하지 않아도 된다.

### 관련 이슈
- [ISSUE-02](../../issues/ISSUE-02_exception_handler_returns_200_for_server_errors.md) — 이 설계가 해결한 결함
