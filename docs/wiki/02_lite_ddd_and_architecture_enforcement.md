# 패키지 계층 분리만으로는 강제되지 않는 의존성 규칙: ArchUnit 도입기

## 문제 상황

도메인이 2~3개일 때는 "도메인 하나당 패키지 하나"(`domain/order`, `domain/product`, ...) 구조로 충분했다. 각 패키지 안에는 컨트롤러, 파사드, 엔티티, 레포지토리가 계층 구분 없이 평평하게 섞여 있었다. 도메인 간 참조가 늘어나면서(`Order`가 `User`/`Product`를 참조하고, `Notification`이 다시 `Order`를 참조하는 식으로) 문제가 드러났다 — 어떤 클래스가 어떤 클래스를 참조해도 되는지를 패키지 구조가 전혀 말해주지 못했다. 컨트롤러가 다른 도메인의 레포지토리를 직접 참조하는 것을 막을 장치가 아무것도 없었다.

## 원인 분석

일반적인 해결책은 각 도메인 패키지 내부를 표현 계층(`presentation`)·응용 계층(`application`)·도메인 계층(`domain`)으로 나누는 것이다. 실제로 그렇게 폴더를 재배치했다.

```
domain/order/
├── presentation/  OrderController, TossWebhookController
├── application/   OrderFacade
└── domain/        Order, OrderItem, OrderStatus, OrderRepository
```

그런데 이렇게 옮긴 뒤에도 검증을 해보니, 표현 계층이 다른 도메인의 도메인 계층을 직접 참조하는 코드가 아무 문제 없이 컴파일됐다.

```java
package com.evo.commerce.domain.product.presentation;

import com.evo.commerce.domain.order.domain.OrderRepository; // 원래 막고 싶었던 참조

public class ProductController {
    private final OrderRepository orderRepository; // 컴파일 에러도, 경고도 없다
}
```

Java의 패키지는 파일을 정리하는 이름 규칙일 뿐, 접근을 물리적으로 차단하는 장치가 아니다. `public class`로 선언된 클래스는 어떤 패키지에서든 자유롭게 import할 수 있다. `presentation`/`application`/`domain`이라는 폴더 이름은 개발자 간의 관례일 뿐, 컴파일러가 검사하는 제약이 아니었다.

## 검토한 대안

**대안 1: package-private 접근 제한자.** `OrderRepository`를 `public`에서 package-private으로 바꾸면 다른 패키지에서의 접근을 막을 수 있다. 하지만 `OrderRepository`는 같은 도메인의 `application` 계층(다른 패키지)에서 정당하게 써야 한다. package-private은 "같은 도메인 내부는 허용, 다른 도메인의 표현 계층에서는 금지"처럼 세밀한 규칙을 표현할 수 없어 기각했다.

**대안 2: 멀티 모듈 빌드 또는 JPMS.** Gradle 멀티 모듈이나 Java 9 모듈 시스템으로 물리적인 컴파일 경계를 만드는 방법. 실수로 참조하는 것 자체를 원천 차단할 수 있지만, 도메인 4개 규모의 프로젝트에 모듈 경계를 새로 설계하고 유지하는 비용이 얻는 이득보다 크다고 판단했다.

**대안 3 (채택): ArchUnit.** "이 패키지는 저 패키지에 의존하면 안 된다"는 규칙을 테스트 코드로 표현하고, 위반 시 테스트가 실패하게 만드는 방법. 물리적 구조를 바꾸지 않고도 세밀한 규칙을 표현할 수 있고, 테스트 의존성 하나만 추가하면 됐다.

## 적용한 해결책

```java
private static final JavaClasses classes = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.evo.commerce.domain");

@Test
void 도메인_계층은_응용이나_표현_계층에_의존하면_안_된다() {
    noClasses().that().resideInAPackage("..domain")
            .should().dependOnClassesThat().resideInAnyPackage("..application", "..presentation")
            .check(classes);
}

@Test
void 표현_계층은_도메인_계층을_직접_참조할_수_없고_응용_계층을_거쳐야_한다() {
    noClasses().that().resideInAPackage("..presentation")
            .should().dependOnClassesThat().resideInAPackage("..domain")
            .check(classes);
}
```

`resideInAPackage("..domain")`처럼 앞에만 `..`을 붙이는 패턴이 핵심이다. 이 프로젝트는 최상위 그룹핑 이름도 `domain`(`com.evo.commerce.domain.order...`)이고 계층 이름도 `domain`(`...order.domain`)이라 같은 단어가 경로에 두 번 나온다. 양쪽에 `..`을 붙이면 최상위 그룹핑 때문에 모든 클래스가 걸려버리므로, 뒤쪽 `..`을 빼서 "경로가 정확히 domain으로 끝나는 것"만 매칭시켰다.

이 규칙을 적용하는 과정에서 실제로 새로운 위반을 하나 더 잡아냈다. MASTER 권한 검사용 애너테이션을 `@RequireRole(UserRole.MASTER)` — 도메인 enum을 타입으로 받는 형태로 처음 설계했는데, ArchUnit이 이걸 위반으로 잡았다.

```
Method <...ProductController.registerProduct(...)> has annotation member
of type <...UserRole> in (ProductController.java:0)
```

애너테이션에 값을 넣는 것도 "그 타입을 참조하는 것"으로 취급된다는 뜻이었다. `@RequireRole(UserRole.MASTER)`라고 쓰는 순간 표현 계층 클래스가 다른 도메인의 도메인 계층 enum을 import하게 되어, 방금 만든 규칙을 스스로 위반했다. `UserRole` 대신 `String` 값을 받도록 바꿔 이 결합을 없앴다 — Spring Security의 `@PreAuthorize("hasRole('MASTER')")`가 타입 안전한 enum이 아니라 문자열을 쓰는 것도 같은 이유다.

## 결과

`./gradlew test`를 돌릴 때마다 두 아키텍처 규칙이 함께 검사된다. 표현 계층이 도메인 계층을 건너뛰거나 다른 도메인을 직접 찌르는 코드를 작성하면 컴파일은 되지만 테스트가 즉시 실패한다. `@RequireRole`도 문자열 기반으로 바뀌어 어떤 도메인의 표현 계층에서 써도 패키지 경계를 넘지 않는다.

> [!NOTE]
> ArchUnit 규칙은 컴파일 에러가 아니라 테스트 실패다. 개발자가 테스트를 돌리지 않고 커밋하거나 규칙 자체를 지워버리면 강제력이 사라진다. 이건 "정직하게 지킬 의지가 있는 팀"의 실수를 잡아주는 도구이지, 우회하려는 사람까지 막는 도구는 아니다 — CI 필수 통과 정책 같은 사회적 장치와 함께 있어야 완전해진다.

## 일반화할 수 있는 점

폴더 재배치와 "아키텍처 규칙을 강제하는 것"은 서로 다른 문제다. 전자는 후자의 필요조건이지 충분조건이 아니다. Java의 패키지 시스템은 이름 규칙일 뿐이라, 물리적 경계(모듈, 접근 제한자)나 테스트 기반 검증(ArchUnit) 중 하나를 의식적으로 선택하지 않으면 "계층을 나눴다"는 사실 자체가 아무것도 보장해주지 않는다.

또 하나, 아키텍처 규칙을 만드는 행위 자체가 새로운 설계 결정을 강제로 드러낸다는 점도 유의할 만하다 — `@RequireRole`의 타입을 무엇으로 할지는 규칙을 적용하기 전까지는 사소해 보였지만, 규칙이 생기자마자 "이 결합이 정말 필요한가"를 다시 묻게 만들었다. 좋은 제약은 문제를 감추는 대신 드러낸다.
