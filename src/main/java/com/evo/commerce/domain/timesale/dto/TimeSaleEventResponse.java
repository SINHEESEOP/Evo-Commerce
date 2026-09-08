package com.evo.commerce.domain.timesale.dto;

import java.time.LocalDateTime;

public record TimeSaleEventResponse(
        Long id,
        Long productId,
        String productName,
        int discountPrice,
        int participantLimit,
        int currentParticipants,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
