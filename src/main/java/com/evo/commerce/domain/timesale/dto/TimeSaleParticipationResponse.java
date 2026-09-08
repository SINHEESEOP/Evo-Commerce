package com.evo.commerce.domain.timesale.dto;

public record TimeSaleParticipationResponse(
        Long participationId,
        Long orderId,
        Long timeSaleEventId
) {
}
