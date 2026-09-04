package com.evo.commerce.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateRequest(
        @NotBlank String name,
        @PositiveOrZero int price,
        @PositiveOrZero int stock
) {
}
