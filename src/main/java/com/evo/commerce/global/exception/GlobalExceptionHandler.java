package com.evo.commerce.global.exception;

import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String detailMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("[Validation Failed] {}", detailMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(detailMessage));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        log.warn("[Business Error] {}", e.getMessage());

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.fail(errorCode.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("[Resource Not Found] URI: {}", request.getRequestURI());

        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.getHttpStatus())
                .body(ApiResponse.fail(CommonErrorCode.RESOURCE_NOT_FOUND.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception e, HttpServletRequest request) {
        log.error("[Unhandled Error] URI: {}", request.getRequestURI(), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
