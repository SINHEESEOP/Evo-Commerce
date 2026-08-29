# [Bug] 회원가입/로그인 API 응답에 비밀번호 해시가 그대로 노출됨

### 증상
- `POST /api/auth/signup`, `POST /api/auth/login`의 JSON 응답에 `password` 필드가 포함되어 있다.
- 저장 자체는 BCrypt로 해시된 값이라 평문은 아니지만, 그 해시 문자열이 그대로 클라이언트(브라우저)까지 전달된다.

### 환경
- `src/main/java/com/evo/commerce/domain/user/UserMapper.java`
- `src/main/java/com/evo/commerce/domain/user/dto/UserResponse.java`
- `src/main/java/com/evo/commerce/domain/user/AuthController.java`

### 재현 절차
1. `docker-compose up -d`로 MySQL/앱을 띄운다.
2. 회원가입을 호출한다.
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/signup \
     -H "Content-Type: application/json" \
     -d '{"email":"leak-test@evo-commerce.com","password":"plain1234!","name":"테스터"}'
   ```
3. 응답 JSON을 확인한다.
   ```json
   {"success":true,"data":{"id":23,"email":"leak-test@evo-commerce.com","password":"$2a$10$9LhKFX5aBIrBC5z74VcY8uOLTKxWkVpuKamW1TCx/FZe92/aUJ.pu","name":"테스터","role":"USER"},"message":null}
   ```
4. 로그인도 동일하게 재현된다.
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"leak-test@evo-commerce.com","password":"plain1234!"}'
   ```
   응답의 `data.user.password`에도 같은 해시 문자열이 그대로 담겨 온다.

### 관찰
- DB에 저장되는 값 자체는 `PasswordEncoder`(BCrypt)로 해시되어 있어 평문 저장 문제는 아니다.
- `UserMapper.toResponse()`가 `User` 엔티티의 모든 필드를 그대로 `UserResponse`에 옮겨 담다 보니, "화면에 보여줘도 되는 필드"와 "절대 밖으로 나가면 안 되는 필드"를 구분하지 않았다.
- 브라우저 개발자 도구 네트워크 탭이나 프록시 로그, 중간 캐시 서버 등 어디에든 이 해시가 그대로 남을 수 있다. 해시라도 유출되면 오프라인 무차별 대입/레인보우 테이블 공격의 표적이 될 수 있다.

### 상태
`[CLOSED]`

### 원인 분석
`UserMapper.toResponse()`가 `User` 엔티티의 필드를 그대로 `UserResponse`에 복사하면서, "응답으로 내보내도 되는 필드"와 "절대 나가면 안 되는 필드"를 구분하지 않고 `password`(BCrypt 해시)까지 옮겨 담았다. `AuthController`의 회원가입/로그인 두 엔드포인트가 이 Mapper와 DTO를 함께 사용해서, 결함 하나가 두 API 응답 모두에 전파됐다.

### 해결 방안
`UserResponse`에서 `password` 컴포넌트를 완전히 제거하고, `UserMapper.toResponse()`도 그 필드를 담지 않도록 수정했다. 필드 단위로 마스킹하는 대신 아예 응답 DTO에서 빼는 화이트리스트 방식을 택했다 — 클라이언트가 이 값을 알아야 할 이유가 없으므로, 안전한 기본값(secure by default)은 "내보낼 필드만 명시"하는 쪽이다. 자세한 내용은 `docs/retrospectives/Step_1.7_리뷰.md` 참고.
