package com.evo.commerce.domain.order.domain;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.OrderErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newProduct;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newUser;

class OrderTest {

    @Test
    void 주문을_생성하면_상태가_CREATED다() {
        Order order = Order.builder()
                .user(newUser())
                .build();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void 주문에_상품을_추가하면_주문_아이템_목록에_담긴다() {
        Order order = Order.builder()
                .user(newUser())
                .build();
        OrderItem orderItem = OrderItem.builder()
                .product(newProduct())
                .quantity(2)
                .build();

        order.addItem(orderItem);

        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getOrderItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void 결제를_완료하면_상태가_PAID로_바뀐다() {
        Order order = Order.builder()
                .user(newUser())
                .build();

        order.pay();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 이미_결제된_주문을_다시_결제하려_하면_예외가_발생한다() {
        Order order = Order.builder()
                .user(newUser())
                .build();
        order.pay();

        assertThatThrownBy(order::pay)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 주문을_취소하면_상태가_CANCELLED로_바뀐다() {
        Order order = Order.builder()
                .user(newUser())
                .build();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 이미_취소된_주문을_다시_취소하려_하면_예외가_발생한다() {
        Order order = Order.builder()
                .user(newUser())
                .build();
        order.cancel();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.INVALID_STATUS_TRANSITION);
    }
}
