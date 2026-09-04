package com.evo.commerce.domain.product.domain;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.evo.commerce.domain.product.domain.ProductTestFixtures.newProduct;

class ProductTest {

    @Test
    void 재고를_주문_수량만큼_차감한다() {
        // given
        Product product = newProduct(10);

        // when
        product.decreaseStock(3);

        // then
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void 재고를_입고_수량만큼_증가시킨다() {
        Product product = newProduct(10);

        product.increaseStock(5);

        assertThat(product.getStock()).isEqualTo(15);
    }

    @Test
    void 재고보다_많은_수량을_차감하려_하면_예외가_발생한다() {
        Product product = newProduct(3);

        assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);

        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void 영_이하의_수량으로_차감하려_하면_예외가_발생한다() {
        Product product = newProduct(10);

        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 재고_확인은_재고를_차감하지_않는다() {
        Product product = newProduct(10);

        product.validateStockAvailable(10);

        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void 재고보다_많은_수량을_확인하면_예외가_발생한다() {
        Product product = newProduct(3);

        assertThatThrownBy(() -> product.validateStockAvailable(10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);
    }
}
