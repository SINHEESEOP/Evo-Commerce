package com.evo.commerce.domain.order.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.order.domain.OrderStatus;
import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.domain.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newProduct;

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
    void 주문을_생성해도_결제_전에는_재고가_차감되지_않는다() {
        User user = userRepository.save(User.builder()
                .email("order-tx-" + System.nanoTime() + "@evo-commerce.com")
                .password("plain1234!")
                .name("테스터")
                .role(UserRole.USER)
                .build());
        userId = user.getId();

        Product product = productRepository.save(newProduct());
        productId = product.getId();

        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(product.getId(), 2))
        );

        OrderResponse response = orderFacade.placeOrder(user.getId(), request);
        orderId = response.id();

        Order reloadedOrder = orderRepository.findById(response.id()).orElseThrow();
        Product reloadedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(reloadedProduct.getStock()).isEqualTo(10);
    }
}
