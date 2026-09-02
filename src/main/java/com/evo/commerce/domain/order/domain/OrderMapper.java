package com.evo.commerce.domain.order.domain;

import com.evo.commerce.domain.order.dto.OrderItemResponse;
import com.evo.commerce.domain.order.dto.OrderResponse;

import java.util.List;

public class OrderMapper {

    public static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderMapper::toItemResponse)
                .toList();

        return new OrderResponse(order.getId(), order.getStatus(), order.calculateTotalAmount(), items);
    }

    private static OrderItemResponse toItemResponse(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getProductSnapshot().productName(),
                orderItem.getProductSnapshot().unitPrice(),
                orderItem.getQuantity()
        );
    }
}
