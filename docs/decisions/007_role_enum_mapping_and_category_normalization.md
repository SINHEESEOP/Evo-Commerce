### 문제 상황
User/Product ERD를 설계하면서, `role`을 어떤 `EnumType`으로 저장할지와 `category`를 지금 시점에 별도 엔티티로 정규화할지 판단이 필요했다.

### 검토한 대안
- `role`을 `EnumType.ORDINAL`로 유지하고, enum 상수 순서를 바꾸지 않도록 코드 리뷰 규칙으로 금지
- `category`를 별도 `Category` 엔티티로 정규화

### 결정 및 이유
`role`은 `EnumType.STRING`을 채택했다. `ORDINAL` + 리뷰 규칙은 규칙 준수가 사람에게 달려 있어 실수를 원천 차단하지 못한다. `category`는 정규화하지 않고 문자열 컬럼으로 유지했다. 현재 요구사항에는 카테고리 계층이나 카테고리별 속성이 없어, 지금 정규화하는 것은 과설계로 판단했다.

### 적용 결과
`role`은 DB에 문자열로 저장돼 enum 순서가 바뀌어도 기존 데이터가 깨지지 않는다. `category`는 필요해지는 시점(계층 구조나 속성이 생길 때)에 재검토하기로 하고 문자열 컬럼으로 남겨뒀다.

### 관련 이슈
없음(Documentation-Only Step). 상세 논의는 `docs/retrospectives/Step_1.2_리뷰.md` 참고.
