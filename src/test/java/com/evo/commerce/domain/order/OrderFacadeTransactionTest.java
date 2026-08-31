package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderFacadeTransactionTest {

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    UserRepository userRepository;

    private Long orderId;
    private Long productId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            orderRepository.deleteById(orderId);
        }
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void 주문을_생성하면_결제_상태와_재고_차감이_DB에도_반영된다() {
        User user = userRepository.save(User.builder()
                .email("order-tx-" + System.nanoTime() + "@evo-commerce.com")
                .password("plain1234!")
                .name("테스터")
                .role(UserRole.USER)
                .build());
        userId = user.getId();

        Product product = productRepository.save(Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build());
        productId = product.getId();

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(product.getId(), 2))
        );

        OrderResponse response = orderFacade.placeOrder(user.getId(), request);
        orderId = response.id();

        Order reloadedOrder = orderRepository.findById(response.id()).orElseThrow();
        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloadedProduct.getStock()).isEqualTo(8);
    }
}
