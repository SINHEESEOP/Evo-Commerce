# [Bug] 상품 등록 API가 로그인 여부만 확인하고 MASTER 권한은 확인하지 않음

### 증상
- `POST /api/products`는 로그인하지 않은 요청은 `401 Unauthorized`로 막지만, `UserRole.USER`인 일반 고객이 로그인 상태로 요청하면 그대로 상품이 등록된다.
- 이 API는 원래 `MASTER` 역할을 가진 사용자만 호출할 수 있어야 한다.

### 환경
- `src/main/java/com/evo/commerce/domain/product/presentation/ProductController.java`
- `src/main/java/com/evo/commerce/global/auth/AuthInterceptor.java`
- `src/main/java/com/evo/commerce/global/auth/JwtAuthenticationFilter.java`

### 재현 절차
1. `UserRole.USER`로 회원가입 후 로그인해 JWT를 발급받는다.
2. 그 토큰으로 `POST /api/products`에 유효한 `ProductCreateRequest` 바디를 담아 요청한다.
   ```java
   String token = jwtTokenProvider.createToken(1L, UserRole.USER);

   mockMvc.perform(post("/api/products")
                   .header("Authorization", "Bearer " + token)
                   .contentType(MediaType.APPLICATION_JSON)
                   .content(objectMapper.writeValueAsString(request)))
           .andExpect(status().isOk());   // 통과한다 — MASTER가 아닌데도 등록에 성공
   ```
3. 응답이 `200 OK`와 함께 등록된 상품 정보를 반환한다 — 역할 검사가 전혀 없기 때문이다.

`ProductControllerTest`의 `로그인한_사용자는_상품을_등록할_수_있다` 테스트가 이 상태를 그대로 재현하는 회귀 테스트다.

### 관찰
- `WebMvcConfig`가 `/api/**`(일부 경로 제외) 전체에 `AuthInterceptor`를 걸어두고 있고, `AuthInterceptor.preHandle()`은 `request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) == null`만 확인한다 — 즉 "로그인했는가"(인증)만 검사하고 "권한이 있는가"(인가)는 검사하지 않는다.
- `JwtAuthenticationFilter`는 토큰의 `role` 클레임을 파싱해 `ROLE_ATTRIBUTE`로 이미 request에 심어두고 있지만, 이 값을 읽어서 역할을 검사하는 코드는 프로젝트 어디에도 없다.
- `ProductController.registerProduct()`는 `AuthInterceptor`가 "이미 보호하고 있다"고 가정한 채로 작성됐다 — 로그인 여부 확인과 권한 확인이 서로 다른 검사라는 걸 구분하지 못한 전형적인 인증/인가 혼동이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
