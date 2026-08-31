package com.evo.commerce.domain.order.dto;

public record OrderItemResponse(
        String productName,
        int unitPrice,
        int quantity
) {
}
