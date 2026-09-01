package com.evo.commerce.domain.user;

final class UserTestFixtures {

    private UserTestFixtures() {
    }

    static User newUser(String email, String password) {
        return User.builder()
                .email(email)
                .password(password)
                .name("테스터")
                .role(UserRole.USER)
                .build();
    }
}
