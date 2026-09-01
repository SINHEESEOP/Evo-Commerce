package com.evo.commerce.domain.order;

public record OrderPaidEvent(Long orderId, Long userId) {
}
