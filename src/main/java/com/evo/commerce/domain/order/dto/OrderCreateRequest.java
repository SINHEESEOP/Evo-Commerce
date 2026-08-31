package com.evo.commerce.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderCreateRequest(
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
