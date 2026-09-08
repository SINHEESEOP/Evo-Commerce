package com.evo.commerce.global.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum TimeSaleErrorCode implements ErrorCode {

    TIME_SALE_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 타임세일 이벤트입니다."),
    TIME_SALE_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 타임세일이 아닙니다."),
    PARTICIPANT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "선착순 참여 인원이 모두 마감되었습니다."),
    ALREADY_PARTICIPATED(HttpStatus.CONFLICT, "이미 참여한 타임세일입니다."),
    PARTICIPATION_TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "일시적으로 요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
