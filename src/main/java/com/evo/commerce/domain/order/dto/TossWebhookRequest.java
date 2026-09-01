package com.evo.commerce.domain.order.dto;

public record TossWebhookRequest(
        String eventType,
        Data data
) {
    public record Data(
            String paymentKey,
            String orderId,
            String status
    ) {
    }
}
