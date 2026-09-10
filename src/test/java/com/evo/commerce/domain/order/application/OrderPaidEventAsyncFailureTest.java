package com.evo.commerce.domain.order.application;

import com.evo.commerce.domain.notification.domain.NotificationRepository;
import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.order.domain.OrderStatus;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.evo.commerce.domain.order.dto.TossWebhookRequest;
import com.evo.commerce.domain.order.infrastructure.OutboxEventPublisher;
import com.evo.commerce.domain.payment.domain.PaymentRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.domain.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    OutboxEventPublisher outboxEventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @MockitoBean
    NotificationRepository notificationRepository;

    private Long orderId;
    private Long productId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        if (orderId != null) {
            outboxEventRepository.findAll().stream()
                    .filter(event -> event.getPayload().contains("\"orderId\":" + orderId))
                    .forEach(event -> outboxEventRepository.deleteById(event.getId()));
            paymentRepository.deleteByOrder_Id(orderId);
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
    void 컨슈머_처리가_실패해도_브로커_컨펌을_받은_아웃박스는_발행_완료로_남는다() {
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
                new TossWebhookRequest.Data("payment-key-1", "ORDER-" + orderId, "DONE", "카드", 10000, OffsetDateTime.now())
        );

        assertThatCode(() -> orderFacade.handlePaymentWebhook(request)).doesNotThrowAnyException();

        OrderStatus reloadedStatus = transactionTemplate.execute(status ->
                orderRepository.findById(orderId).orElseThrow().getStatus());
        assertThat(reloadedStatus).isEqualTo(OrderStatus.PAID);

        outboxEventPublisher.publishPendingEvents();

        OutboxEventStatus outboxStatus = transactionTemplate.execute(status ->
                outboxEventRepository.findAll().stream()
                        .filter(event -> event.getPayload().contains("\"orderId\":" + orderId))
                        .findFirst()
                        .orElseThrow()
                        .getStatus());
        assertThat(outboxStatus).isEqualTo(OutboxEventStatus.SENT);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                verify(notificationRepository).save(any()));
    }
}
