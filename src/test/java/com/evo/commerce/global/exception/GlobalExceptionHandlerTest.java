package com.evo.commerce.global.exception;

import com.evo.commerce.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void 예외가_발생하면_실패_응답을_반환한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new IllegalStateException("잔액 부족"));

        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("잔액 부족");
    }
}
