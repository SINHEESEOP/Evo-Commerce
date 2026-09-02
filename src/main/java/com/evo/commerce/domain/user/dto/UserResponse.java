package com.evo.commerce.domain.user.dto;

import com.evo.commerce.domain.user.domain.UserRole;

public record UserResponse(
        Long id,
        String email,
        String name,
        UserRole role
) {
}
