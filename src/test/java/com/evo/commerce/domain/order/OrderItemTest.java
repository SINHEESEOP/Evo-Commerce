package com.evo.commerce.domain.order;

import com.evo.commerce.domain.product.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void 상품으로_주문_아이템을_생성하면_상품_스냅샷을_저장한다() {
        Product product = Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();

        OrderItem orderItem = OrderItem.builder()
                .product(product)
                .quantity(3)
                .build();

        assertThat(orderItem.getProductSnapshot().productName()).isEqualTo("테스트 상품");
        assertThat(orderItem.getProductSnapshot().unitPrice()).isEqualTo(10000);
        assertThat(orderItem.getQuantity()).isEqualTo(3);
    }
}
