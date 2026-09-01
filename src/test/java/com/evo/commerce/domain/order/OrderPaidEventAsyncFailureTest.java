package com.evo.commerce.domain.order;

import com.evo.commerce.domain.notification.NotificationRepository;
import com.evo.commerce.domain.order.dto.TossWebhookRequest;
import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.domain.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OrderPaidEventAsyncFailureTest {

    @Autowired
    OrderFacade orderFacade;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    NotificationRepository notificationRepository;

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
    void 알림_저장이_실패해도_웹훅_처리_자체는_예외_없이_끝난다() {
        User user = userRepository.save(User.builder()
                .email("notify-async-" + System.nanoTime() + "@evo-commerce.com")
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

        Order order = Order.builder().user(user).build();
        order.addItem(OrderItem.builder().product(product).quantity(1).build());
        Order saved = orderRepository.save(order);
        orderId = saved.getId();

        given(notificationRepository.save(any())).willThrow(new RuntimeException("알림 저장 실패 가정"));

        TossWebhookRequest request = new TossWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                new TossWebhookRequest.Data("payment-key-1", orderId.toString(), "DONE")
        );

        assertThatCode(() -> orderFacade.handlePaymentWebhook(request)).doesNotThrowAnyException();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);

        verify(notificationRepository, timeout(2000)).save(any());
    }
}
