package com.evo.commerce.domain.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void 재고를_주문_수량만큼_차감한다() {
        Product product = Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();

        product.decreaseStock(3);

        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void 재고를_입고_수량만큼_증가시킨다() {
        Product product = Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();

        product.increaseStock(5);

        assertThat(product.getStock()).isEqualTo(15);
    }
}
