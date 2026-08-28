# [Bug] JWT 서명 비밀키가 소스 코드에 하드코딩되어 토큰 위조가 가능함

### 증상
- `JwtTokenProvider`가 토큰 서명에 사용하는 비밀키(`SECRET_KEY`)가 클래스 내부에 문자열 상수로 선언되어 있다.
- 이 값은 `application.yaml`이나 환경 변수가 아닌 `.java` 소스 파일에 그대로 존재하므로, 저장소에 접근 가능한 모든 사람(git clone, git log, IDE 열람 포함)이 평문으로 확인할 수 있다.
- 정상적인 로그인 절차를 거치지 않고도, 이 문자열만 알면 임의의 `userId`/`role`로 서명이 유효한 토큰을 직접 만들 수 있다.

### 환경
- `src/main/java/com/evo/commerce/global/auth/JwtTokenProvider.java`
- `src/main/java/com/evo/commerce/global/auth/JwtAuthenticationFilter.java`
- `src/main/java/com/evo/commerce/global/auth/AuthInterceptor.java`
- `io.jsonwebtoken:jjwt-api:0.12.6` (HS256 서명)

### 재현 절차
1. 저장소에서 `JwtTokenProvider.SECRET_KEY` 값을 그대로 읽는다.
   ```java
   private static final String SECRET_KEY = "evo-commerce-jwt-secret-key-for-token-signing-2024";
   ```
2. 로그인 API 없이, 위 문자열만으로 별도의 jjwt 코드를 작성해 `role=ADMIN`, `subject=999`(존재하지 않는 사용자 ID)인 토큰을 직접 서명해 만든다.
   ```java
   SecretKey key = Keys.hmacShaKeyFor(
       "evo-commerce-jwt-secret-key-for-token-signing-2024".getBytes());
   String forgedToken = Jwts.builder()
       .subject("999")
       .claim("role", "ADMIN")
       .signWith(key)
       .compact();
   ```
3. 이 `forgedToken`을 `Authorization: Bearer {forgedToken}` 헤더에 담아 인증이 필요한 API(`/api/**`)에 요청한다.
4. `JwtAuthenticationFilter`가 서명 검증을 통과시키고, `AuthInterceptor`도 정상 요청으로 판단해 그대로 통과시킨다. 로그인을 한 적이 없는데도 임의 사용자·임의 권한으로 API에 접근할 수 있다.

### 관찰
- `JwtTokenProvider`의 `validateToken()`은 서명이 올바른지만 확인한다. 서명 검증 로직 자체는 정상 동작하지만, 검증에 쓰이는 키가 공개된 값이라는 것이 문제다.
- 키가 소스 코드에 있으면 dev/stage/prod 등 모든 환경이 같은 키를 공유하게 되고, 유출 시에도 코드 배포 없이는 키를 교체(rotate)할 수 없다.
- git 이력에 한 번이라도 커밋되면, 이후 커밋에서 값을 지워도 과거 커밋 기록에는 그대로 남는다.

### 상태
`[OPEN]`

### 원인 분석
(해결 시 작성)

### 해결 방안
(해결 시 작성)
