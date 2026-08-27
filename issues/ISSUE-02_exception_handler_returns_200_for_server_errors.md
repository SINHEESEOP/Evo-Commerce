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
`[OPEN]`

### 원인 분석
(해결 완료 시 작성)

### 해결 방안
(해결 완료 시 작성)
