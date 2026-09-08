package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;

public class TimeSaleMapper {

    public static TimeSaleEventResponse toResponse(TimeSaleEvent event, long currentParticipants) {
        return new TimeSaleEventResponse(
                event.getId(),
                event.getProduct().getId(),
                event.getProduct().getName(),
                event.getDiscountPrice(),
                event.getParticipantLimit(),
                (int) currentParticipants,
                event.getStartAt(),
                event.getEndAt()
        );
    }
}
