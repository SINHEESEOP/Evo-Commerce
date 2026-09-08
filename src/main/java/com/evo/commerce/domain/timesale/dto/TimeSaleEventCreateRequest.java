package com.evo.commerce.domain.timesale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record TimeSaleEventCreateRequest(
        @NotNull Long productId,
        @Positive int discountPrice,
        @Positive int participantLimit,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt
) {
}
