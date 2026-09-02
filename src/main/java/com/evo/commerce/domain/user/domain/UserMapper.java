package com.evo.commerce.domain.user.domain;

import com.evo.commerce.domain.user.dto.UserResponse;

public class UserMapper {

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );
    }
}
