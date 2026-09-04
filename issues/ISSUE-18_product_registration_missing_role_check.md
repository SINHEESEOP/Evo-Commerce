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
`[CLOSED]`

### 원인 분석
`AuthInterceptor`가 `/api/**`에 대해 "로그인했는가"(인증)만 검사하고 "권한이 있는가"(인가)는 검사하지 않는데, `ProductController.registerProduct()`를 작성하면서 전자가 후자를 포함한다고 착각했다. `JwtAuthenticationFilter`가 이미 request에 심어두고 있는 역할 정보(`ROLE_ATTRIBUTE`)를 읽어서 검사하는 코드가 어디에도 없었다.

### 해결 방안
`@RequireRole` 메서드 애너테이션을 추가하고, `AuthInterceptor.preHandle()`이 `HandlerMethod`에서 이 애너테이션을 읽어 요구된 역할과 현재 사용자의 역할(`ROLE_ATTRIBUTE`)을 비교하도록 확장했다. `ProductController.registerProduct()`에 `@RequireRole("MASTER")`를 붙였고, 역할이 다르면 `AuthErrorCode.FORBIDDEN`(403)을 던진다.

`@RequireRole`의 값 타입은 처음에 도메인의 `UserRole` enum으로 설계했으나, `PackageArchitectureTest`(ArchUnit)가 "표현 계층은 도메인 계층에 의존할 수 없다" 규칙 위반으로 잡아냈다 — `ProductController`(표현 계층)가 `UserRole`(user 도메인의 도메인 계층)을 애너테이션 인자로 참조하게 되기 때문이다. Spring Security의 `@PreAuthorize("hasRole('MASTER')")`가 타입 안전한 enum 대신 문자열 리터럴을 쓰는 것과 같은 이유로, `@RequireRole`도 `String` 값을 받도록 바꿔 이 결합을 없앴다. 컨트롤러 개별 검사 대신 커스텀 애너테이션 + 인터셉터 확장을 택한 이유, 그리고 Spring Security 전면 도입을 보류한 이유는 `docs/bug_intents/Step_2.9_질문답변.md`에 정리했다.

Spring Security의 `@PreAuthorize` 전면 도입은 인증/인가 요구사항이 역할 하나 이상으로 복잡해질 때(예: 리소스 소유자 확인) 재검토하기로 하고 지금은 채택하지 않았다.
