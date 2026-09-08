package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;

public class TimeSaleMapper {

    public static TimeSaleEventResponse toResponse(TimeSaleEvent event) {
        return new TimeSaleEventResponse(
                event.getId(),
                event.getProduct().getId(),
                event.getProduct().getName(),
                event.getDiscountPrice(),
                event.getParticipantLimit(),
                event.getCurrentParticipants(),
                event.getStartAt(),
                event.getEndAt()
        );
    }
}
