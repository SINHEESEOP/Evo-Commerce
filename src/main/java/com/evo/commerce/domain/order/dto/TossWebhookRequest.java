package com.evo.commerce.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossWebhookRequest(
        String eventType,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String paymentKey,
            String orderId,
            String status,
            String method,
            int totalAmount,
            OffsetDateTime approvedAt
    ) {
    }
}
