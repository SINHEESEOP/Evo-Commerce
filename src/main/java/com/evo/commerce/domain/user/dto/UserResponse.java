package com.evo.commerce.domain.user.dto;

import com.evo.commerce.domain.user.UserRole;

public record UserResponse(
        Long id,
        String email,
        String name,
        UserRole role
) {
}
