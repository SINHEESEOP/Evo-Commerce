package com.evo.commerce.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncConfigTest {

    @Test
    void 비동기_예외_핸들러가_등록되어_있고_예외가_나도_추가로_던지지_않는다() throws NoSuchMethodException {
        AsyncConfig asyncConfig = new AsyncConfig();
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        Method dummyMethod = Object.class.getMethod("toString");

        assertThat(handler).isNotNull();
        assertThatCode(() -> handler.handleUncaughtException(new RuntimeException("test"), dummyMethod))
                .doesNotThrowAnyException();
    }
}
