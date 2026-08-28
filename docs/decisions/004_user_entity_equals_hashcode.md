### 문제 상황
`User` 엔티티에 붙인 Lombok `@Data`가 만든 `equals()`/`hashCode()`가 DB가 채번하는 `id`를 포함한 모든 필드를 기준으로 계산되어, 저장 전/후로 같은 객체의 `hashCode`가 달라지고 `HashSet`/`HashMap`이 그 객체를 잃어버렸다.

### 검토한 대안
- `@EqualsAndHashCode(of = "id")`로 `id`만 기준으로 좁히기
- `equals`는 `id` 기반, `hashCode`는 클래스 단위 상수(`getClass().hashCode()`)로 분리해서 직접 재정의

### 결정 및 이유
후자를 선택했다. `id`만 기준으로 좁혀도 `id` 자체가 저장 전후로 바뀌는 필드라 문제가 그대로 남는다. `hashCode`를 아예 상수로 고정하면 저장 전/후로 절대 값이 바뀌지 않아 `HashSet`이 항상 같은 버킷을 찾고, 그 버킷 안에서는 `equals`(참조 동일성 → `id` 비교 순서)로 정확히 구분된다.

### 적용 결과
모든 `User` 인스턴스가 같은 해시값을 공유해 `HashSet` 내부 분산은 이론적으로 나빠지지만, 이 프로젝트 규모에서는 정확성이 우선이라 감수했다. 저장 전 컬렉션에 담아둔 엔티티를 저장 후에도 찾을 수 있는지 회귀 테스트로 검증했다.

### 관련 이슈
[ISSUE-05](../../issues/ISSUE-05_user_entity_tostring_leaks_password.md)
