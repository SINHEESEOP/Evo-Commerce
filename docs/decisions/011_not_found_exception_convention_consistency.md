### 문제 상황
존재하지 않는 상품 상세 조회 시 404 대신 500이 반환되는 결함을 고치면서, 이 결함을 위해 별도의 예외 처리 경로를 새로 만들지 판단이 필요했다.

### 검토한 대안
- `GlobalExceptionHandler`에 `IllegalArgumentException` 전용 핸들러를 추가
- 기존 `ErrorCode` + `BusinessException` 컨벤션에 맞춰 `ProductErrorCode`를 추가

### 결정 및 이유
후자를 채택했다. 이 프로젝트는 이미 `ErrorCode` + `BusinessException` 조합을 표준 컨벤션으로 쓰고 있다. 예외 타입별로 핸들러를 계속 늘리는 대신 기존 컨벤션에 맞추는 쪽이 일관성 유지에 유리하다고 판단했다.

### 적용 결과
`ProductErrorCode.PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, ...)`를 추가하고 `ProductService.getProduct()`가 이를 던지도록 했다. `GlobalExceptionHandler`의 기존 `BusinessException` 처리 경로를 그대로 재사용해 별도 핸들러 추가 없이 404가 응답된다.

### 관련 이슈
[ISSUE-09](../../issues/ISSUE-09_product_detail_not_found_returns_500.md)
