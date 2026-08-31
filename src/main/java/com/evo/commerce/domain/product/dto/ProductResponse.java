package com.evo.commerce.domain.product.dto;

public record ProductResponse(
        Long id,
        String name,
        int price,
        int stock
) {
}
