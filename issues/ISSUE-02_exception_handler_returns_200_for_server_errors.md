# [Bug] 전역 예외 처리기가 서버 오류에도 HTTP 200을 응답

### 증상
- 컨트롤러 계층에서 처리되지 않은 예외가 발생해도, 클라이언트가 받는 HTTP 상태 코드는 항상 `200 OK`다.
- 심지어 존재하지 않는 경로로 요청을 보내도(원래는 `404`여야 함) `200`이 반환된다.
- 응답 바디에는 `{"success": false, "data": null, "message": "..."}` 형태로 실패 여부가 담기지만, 상태 코드만 보고 판단하는 클라이언트/모니터링/로드밸런서 입장에서는 정상 응답과 구분할 수 없다.
- `message` 필드에 예외의 `getMessage()` 값이 그대로 노출된다.

### 환경
- `src/main/java/com/evo/commerce/global/exception/GlobalExceptionHandler.java`
- `src/main/java/com/evo/commerce/global/response/ApiResponse.java`
- Spring Boot 3.5.3, `spring-boot-starter-web`

### 재현 절차
1. 인프라와 앱을 기동한다.
   ```bash
   docker-compose up -d
   ./gradlew bootRun
   ```
2. 존재하지 않는 임의의 경로를 호출한다.
   ```bash
   curl -i http://localhost:8080/anything
   ```
3. 응답을 확인한다.
   ```
   HTTP/1.1 200
   Content-Type: application/json

   {"success":false,"data":null,"message":"No static resource anything."}
   ```
4. 원래 `404`가 나와야 할 요청조차 `200`으로 응답되고, 프레임워크 예외 메시지(`No static resource anything.`)가 그대로 바디에 노출되는 것을 확인한다. 이후 실제 도메인 예외(재고 부족, 잘못된 요청 등)가 발생해도 동일한 방식으로 처리된다.

### 관찰
- `GlobalExceptionHandler.handleException`이 `ResponseEntity.ok(...)`로 응답을 생성하고 있어, 실제 예외 종류나 성격과 무관하게 항상 `200`이 반환된다.
- `@ExceptionHandler(Exception.class)` 하나로 모든 예외를 잡고 있어, 클라이언트 잘못(잘못된 요청)과 서버 내부 오류를 구분하지 못한다.
- `e.getMessage()`를 그대로 응답에 담고 있어, 이후 DB/외부 API 연동 코드가 추가되면 내부 예외 메시지가 그대로 클라이언트에 노출될 위험이 있다.

### 상태
`[CLOSED]`

### 원인 분석
`ResponseEntity.ok(...)`로 모든 예외를 무조건 `200 OK`로 응답한 것이 근본 원인이다. 예외의 성격(클라이언트 잘못 vs 서버 내부 장애)을 전혀 구분하지 않고 `@ExceptionHandler(Exception.class)` 하나에서 획일적으로 처리했기 때문에, HTTP 상태 코드가 실제 처리 결과와 무관하게 항상 성공을 의미하게 됐다. 또한 `e.getMessage()`를 그대로 응답에 실어, 예외 원문이 클라이언트에 노출되는 정보 노출 위험도 함께 갖고 있었다.

### 해결 방안
예외를 3단계로 계층화해 각각 별도 핸들러로 분리했다.
- `MethodArgumentNotValidException` → `400 Bad Request`, 필드별 검증 오류를 조합한 메시지 반환
- `BusinessException`(`ErrorCode` 보유) → `errorCode.getHttpStatus()`로 상태 코드를 동적으로 매핑
- 그 외 `Exception` → `500 Internal Server Error`, 클라이언트에는 정제된 고정 메시지만 반환하고 서버 로그에는 `e`를 포함한 전체 스택트레이스를 `ERROR` 레벨로 기록

`4xx`는 `log.warn`으로 한 줄만, `5xx`는 `log.error`로 스택트레이스까지 남겨 로그 레벨과 노출 정보를 분리했다. `ErrorCode`가 상태 코드와 메시지를 스스로 갖게 해, 핸들러가 예외 타입별로 상태 코드를 분기 판단할 필요가 없도록 했다.
