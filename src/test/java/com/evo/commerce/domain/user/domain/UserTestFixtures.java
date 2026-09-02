package com.evo.commerce.domain.user.domain;

public final class UserTestFixtures {

    private UserTestFixtures() {
    }

    public static User newUser(String email, String password) {
        return User.builder()
                .email(email)
                .password(password)
                .name("테스터")
                .role(UserRole.USER)
                .build();
    }
}
