package com.evo.commerce.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentConfirmResponse(
        String paymentKey,
        String orderId,
        String method,
        int totalAmount,
        OffsetDateTime approvedAt
) {
}
