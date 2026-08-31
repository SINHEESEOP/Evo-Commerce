# [Bug] 존재하지 않는 상품 상세 조회 시 404 대신 500이 반환됨

### 증상
- `GET /api/products/{id}`를 존재하지 않는 `id`로 호출하면 `404 Not Found`가 아니라 `500 Internal Server Error`가 반환된다.
- 응답 `message`는 실제 원인("존재하지 않는 상품입니다")이 아니라 `CommonErrorCode.INTERNAL_SERVER_ERROR`의 일반 메시지("서버 내부에 오류가 발생했습니다")로 나온다.

### 환경
- `src/main/java/com/evo/commerce/domain/product/ProductService.java`
- `src/main/java/com/evo/commerce/global/exception/GlobalExceptionHandler.java`

### 재현 절차
1. 로그인해서 발급받은 토큰으로 존재하지 않는 상품 id를 조회한다.
   ```bash
   curl -H "Authorization: Bearer {token}" http://localhost:8080/api/products/99999
   ```
2. HTTP 상태 코드가 `500`으로 찍히고, 로그에도 `[Unhandled Error]`로 스택 트레이스가 남는다.

### 관찰
- `ProductService.getProduct()`는 `productRepository.findById(id)`가 비어 있을 때 `IllegalArgumentException`을 던진다.
- `GlobalExceptionHandler`는 `MethodArgumentNotValidException`, `BusinessException`만 개별적으로 처리하고, 그 외 모든 예외(`IllegalArgumentException` 포함)는 `handleUnexpectedException()`으로 떨어져 `500`으로 응답한다.
- "상품이 존재하지 않는다"는 클라이언트 입력(경로 변수 `id`)에 따라 정상적으로 발생할 수 있는 비즈니스 상황인데, 서버 내부 오류와 동일하게 취급되고 있다.

### 상태
`[CLOSED]`

### 원인 분석
`ProductService.getProduct()`가 상품이 존재하지 않는 상황을 `IllegalArgumentException`으로 표현했는데, `GlobalExceptionHandler`는 `BusinessException`만 `ErrorCode`가 정의한 상태 코드로 매핑하고 나머지 예외는 전부 500으로 처리한다. 예외를 던지는 형태 자체는 맞았지만, 이 프로젝트의 표준 컨벤션(`ErrorCode` + `BusinessException`)을 따르지 않아서 정상적인 비즈니스 상황이 서버 오류로 취급됐다.

### 해결 방안
`ProductErrorCode`에 `PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다.")`를 추가하고, `ProductService.getProduct()`가 `BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)`를 던지도록 수정했다. `GlobalExceptionHandler`의 기존 `BusinessException` 처리 경로를 그대로 재사용해 별도의 핸들러 추가 없이 404가 응답된다. 상세 트레이드오프는 `docs/retrospectives/Step_1.9_리뷰.md` 참고.
