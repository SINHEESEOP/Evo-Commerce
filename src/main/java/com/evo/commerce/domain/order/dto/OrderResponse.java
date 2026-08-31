package com.evo.commerce.domain.order.dto;

import com.evo.commerce.domain.order.OrderStatus;

import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        List<OrderItemResponse> items
) {
}
