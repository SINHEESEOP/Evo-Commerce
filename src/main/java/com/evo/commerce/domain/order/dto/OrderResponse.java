package com.evo.commerce.domain.order.dto;

import com.evo.commerce.domain.order.domain.OrderStatus;

import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        int totalAmount,
        List<OrderItemResponse> items
) {
}
