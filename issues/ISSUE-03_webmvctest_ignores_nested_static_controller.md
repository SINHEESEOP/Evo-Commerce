# [Bug] WebMvcTest가 테스트 클래스 내부 정적 클래스를 컨트롤러 빈으로 인식하지 못함

### 증상
- `@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)`처럼, 테스트 클래스 내부에 정의한 `public static` 중첩 클래스를 `controllers` 속성에 지정해도 실제로는 해당 컨트롤러가 요청을 받지 않는다.
- 대신 Spring의 기본 정적 리소스 핸들러(`ResourceHttpRequestHandler`)가 요청을 받아, `NoResourceFoundException`이 발생한다.
- `GlobalExceptionHandler`가 이 예외까지 잡아 200으로 응답해버려서, 처음에는 단순히 "assertion 값이 다르다"는 실패로만 보이고 원인이 바로 드러나지 않는다.

### 환경
- Spring Boot 3.5.3, `spring-boot-starter-test` (`@WebMvcTest`, `MockMvc`)
- JUnit 5

### 재현 절차
1. 테스트 클래스 안에 `@RestController`가 붙은 `public static` 중첩 클래스를 정의한다.
2. `@WebMvcTest(controllers = 외부클래스.중첩클래스.class)`로 슬라이스 테스트를 구성한다.
3. `MockMvc`로 중첩 클래스에 정의된 엔드포인트를 호출한다.
   ```bash
   ./gradlew test --tests "com.evo.commerce.global.exception.GlobalExceptionHandlerTest"
   ```
4. 실패 로그의 `Handler` 항목이 컨트롤러가 아니라 `ResourceHttpRequestHandler`로 찍히는 것을 확인한다.
   ```
   Resolved Exception: Type = org.springframework.web.servlet.resource.NoResourceFoundException
   ```

### 관찰
- 동일한 클래스를 테스트 클래스 바깥으로 꺼내 별도 최상위(top-level) 클래스로 옮기면 정상적으로 빈이 등록되고 라우팅된다.
- 즉 원인은 어노테이션이나 접근 제어자(`public`/`static`)가 아니라, "테스트 클래스 내부 중첩 클래스"라는 위치 자체다.

### 상태
`[CLOSED]`

### 원인 분석
`@WebMvcTest(controllers = ...)`로 지정한 클래스는 애플리케이션 컨텍스트에 빈으로 등록되기 위해 Spring Boot 테스트 슬라이스의 컴포넌트 스캔 후보 목록에 포함돼야 한다. 그런데 JUnit 테스트 클래스 자체는 이 스캔의 루트가 아니며, 그 내부의 중첩 클래스는 별도 컴파일 단위(`Outer$Inner.class`)로 취급되어 스캔 대상에서 빠진다. 결과적으로 `DispatcherServlet`에 어떤 `@RequestMapping` 핸들러도 등록되지 않고, 매핑되지 않은 요청은 모두 기본 정적 리소스 핸들러로 폴백된다.

### 해결 방안
- 대안 1: 중첩 클래스를 유지한 채 `@Import`로 명시적으로 빈 등록 — 실제로 테스트해보면 정상 동작한다. `@WebMvcTest(controllers = ...)`의 컨트롤러 후보 필터링 경로만 중첩 클래스를 인식 못하는 것이고, `@Import`는 이 필터링을 거치지 않는 일반적인 스프링 빈 등록 경로라 중첩 여부와 무관하게 등록된다. 다만 슬라이스 테스트마다 `@Import`를 매번 명시해야 하고, 더미 컨트롤러가 테스트 클래스 안에 갇혀 있어 다른 테스트에서 재사용하기 어렵다.
- 대안 2 (채택): 더미 컨트롤러를 최상위 클래스로 추출해 `support` 하위 패키지에 둔다. 파일이 하나 늘지만, 여러 슬라이스 테스트가 필요해지면 그대로 재사용할 수 있고 `@WebMvcTest(controllers = ...)`만으로 등록되어 `@Import` 선언을 매번 반복하지 않아도 된다.
- 최종 적용: `ThrowingController`를 `com.evo.commerce.global.exception.support` 패키지의 최상위 클래스로 유지.
