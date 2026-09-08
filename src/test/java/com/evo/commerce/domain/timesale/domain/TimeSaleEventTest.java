package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newEvent;
import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSaleEventTest {

    @Test
    void 시작_시각과_종료_시각_사이에는_예외없이_통과한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusHours(1), now.plusHours(1));

        assertThatCode(() -> event.validateInProgress(now)).doesNotThrowAnyException();
    }

    @Test
    void 시작_시각_이전이면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.plusHours(1), now.plusHours(2));

        assertThatThrownBy(() -> event.validateInProgress(now))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.TIME_SALE_NOT_IN_PROGRESS);
    }

    @Test
    void 종료_시각_이후이면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusHours(2), now.minusHours(1));

        assertThatThrownBy(() -> event.validateInProgress(now))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.TIME_SALE_NOT_IN_PROGRESS);
    }
}
