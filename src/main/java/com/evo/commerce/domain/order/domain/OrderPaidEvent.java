package com.evo.commerce.domain.order.domain;

public record OrderPaidEvent(Long orderId, Long userId) {
}
