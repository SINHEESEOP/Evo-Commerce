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
   private static final String SECRET_KEY = "evo-commerce-jwt-secret-key-for-token-signing-2026";
   ```
2. 로그인 API 없이, 위 문자열만으로 별도의 jjwt 코드를 작성해 `role=ADMIN`, `subject=999`(존재하지 않는 사용자 ID)인 토큰을 직접 서명해 만든다.
   ```java
   SecretKey key = Keys.hmacShaKeyFor(
       "evo-commerce-jwt-secret-key-for-token-signing-2026".getBytes());
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
`[CLOSED]`

### 원인 분석
JWT 서명에 쓰는 `SecretKey`를 만들 때, 그 원본 문자열을 `application.yaml` 같은 설정이 아니라 `JwtTokenProvider` 클래스 내부의 `private static final String` 상수로 선언했다. 자바 컴파일 타임 상수는 소스 코드뿐 아니라 컴파일된 `.class` 파일의 constant pool에도 그대로 문자열로 저장되므로, git 저장소 접근 권한이 없어도 배포된 `.jar`/Docker 이미지만 손에 넣으면(`unzip` + `strings`, 또는 디컴파일) 키를 그대로 추출할 수 있다. 검증 로직(`validateToken()`, `parseClaims()`) 자체는 정상 동작했고, 정확히 그 정직함 때문에 위조된 토큰도 "서명이 맞다"며 통과시켰다.

### 해결 방안
`SECRET_KEY` 상수를 제거하고, `application.yaml`의 `jwt.secret` 프로퍼티(`${JWT_SECRET}`, 기본값 없음)를 생성자에서 `@Value("${jwt.secret}")`로 주입받는 방식으로 바꿨다. 기본값을 아예 두지 않아서, 로컬이든 `prod`든 `JWT_SECRET` 환경 변수가 없으면 `PlaceholderResolutionException`으로 기동 자체가 실패한다 — 두 경우 모두 재현해서 확인했다. `@Value`를 필드가 아니라 생성자 파라미터로 받은 이유는, 스프링의 필드 주입이 생성자 실행 이후에 일어나서 필드 초기화 블록에서 곧바로 참조하면 `NullPointerException`이 나기 때문이다.

중간에 로컬 개발 편의를 위해 `application.yaml`에 폴백 기본값을 남기거나, 그 기본값을 `application-local.yaml`(git 추적 제외)로 분리하는 방식도 시도했다. 하지만 후자는 이미 `application.yaml` 공통 블록에 `datasource` 같은 로컬 전용 값이 프로필 구분 없이 박혀 있는 기존 컨벤션과 충돌해서(같은 "로컬 값"인데 프로퍼티마다 관리 위치가 달라짐) 되돌렸다. 최종적으로는 로컬 전용 파일/프로필 없이 "시크릿은 예외 없이 항상 환경 변수로만 공급한다"는 단일 규칙으로 정리했다. 별도의 `@ConfigurationProperties` 클래스나 외부 시크릿 관리 서비스(Vault 등) 도입은 프로퍼티가 하나뿐인 현재 규모에서는 과설계로 판단해 적용하지 않았다. 과거 커밋에 이미 남아 있던 예전 하드코딩 값은 git 이력 자체를 재작성하지 않는 한 남아있으므로, 실제 배포에서는 그 값을 유출된 것으로 간주하고 반드시 다른 `JWT_SECRET`을 쓴다. 자세한 내용은 `docs/retrospectives/Step_1.6_리뷰.md` 참고.
