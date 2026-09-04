# [Bug] 로그인만 하면 역할과 무관하게 "상품 등록" 메뉴가 노출됨

### 증상
- `UserRole.USER`인 일반 고객으로 로그인해도 네비게이션에 "상품 등록" 링크가 보인다.
- 그 링크를 클릭해 `product-register.html`에서 폼을 채우고 제출하면, 그제서야 `403 Forbidden`(`이 작업을 수행할 권한이 없습니다.`) 응답을 받는다.

### 환경
- `src/main/resources/static/js/nav.js`
- `src/main/resources/static/login.html`
- `src/main/java/com/evo/commerce/domain/user/dto/LoginResponse.java` (역할 정보가 이미 응답에 포함돼 있음)

### 재현 절차
1. `UserRole.USER`로 회원가입 후 로그인한다.
2. 어느 페이지에서든 네비게이션 우측을 보면 "상품 등록" 링크가 보인다.
3. 그 링크를 클릭해 `product-register.html`로 이동한 뒤, 상품명/가격/재고를 채우고 "등록하기"를 누른다.
4. `POST /api/products`가 `403`을 응답하고, 화면에 "등록 실패: 이 작업을 수행할 권한이 없습니다."가 표시된다 — 폼을 다 채운 뒤에야 권한이 없다는 걸 알게 된다.

### 관찰
- `nav.js`는 `localStorage.getItem('accessToken')`의 존재 여부만으로 `#nav-register`(상품 등록 링크)의 `hidden` 속성을 토글한다 — 로그인했는지만 확인하고, 그 사용자의 역할은 확인하지 않는다.
- `login.html`은 로그인 응답의 `body.data.token`만 `localStorage`에 저장하고, 같은 응답에 포함된 `body.data.user.role`은 어디에도 저장하지 않는다 — 정보 자체는 서버가 이미 내려주고 있는데 프론트엔드가 활용하지 않는다.
- 백엔드는 `@RequireRole("MASTER")`(Step 2.9)로 이미 안전하게 막고 있으므로 이건 보안 취약점은 아니다 — 그러나 권한 없는 사용자가 폼을 다 채운 뒤에야 실패를 알게 되는 건 불필요한 혼란이다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성 예정)

### 해결 방안
(해결 시 작성 예정)
