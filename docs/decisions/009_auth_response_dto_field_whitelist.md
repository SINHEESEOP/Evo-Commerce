### 문제 상황
`UserResponse`가 `User` 엔티티를 그대로 베껴 쓰면서 `password`(해시)까지 응답에 그대로 노출됐다. 어떤 방식으로 이 필드를 응답에서 제외할지 판단이 필요했다.

### 검토한 대안
- `@ToString.Exclude`처럼 필드 단위로 제외 표시를 하는 방식
- 응답 DTO를 아예 "내보낼 필드만" 선언하는 화이트리스트 방식
- 회원가입/로그인 응답 DTO를 각각 별도로 분리

### 결정 및 이유
화이트리스트 방식을 채택했다. `record`는 필드를 선택적으로 감추는 애노테이션을 지원하지 않고, 애초에 응답 DTO는 "내보낼 필드만 선언"하는 것이 안전한 기본값(secure by default)이다. 응답 DTO 분리는 하지 않았다 — 회원가입/로그인 두 API가 필요로 하는 필드가 지금은 동일해서 분리할 실익이 없다고 판단했다.

### 적용 결과
`UserResponse`에서 `password` 컴포넌트를 완전히 제거하고 `UserMapper.toResponse()`도 그 필드를 담지 않도록 했다. 두 API의 응답 요구사항이 갈리기 시작하면 그때 DTO를 분리하기로 했다.

### 관련 이슈
[ISSUE-07](../../issues/ISSUE-07_password_hash_leaked_in_auth_response.md)
