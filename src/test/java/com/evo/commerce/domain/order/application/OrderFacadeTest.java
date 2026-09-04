package com.evo.commerce.domain.order.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.order.domain.OrderStatus;
import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.order.dto.PaymentConfirmRequest;
import com.evo.commerce.domain.order.dto.TossPaymentConfirmResponse;
import com.evo.commerce.domain.order.dto.TossWebhookRequest;
import com.evo.commerce.domain.order.infrastructure.TossPaymentClient;
import com.evo.commerce.domain.payment.domain.Payment;
import com.evo.commerce.domain.payment.domain.PaymentRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.OrderErrorCode;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newProduct;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newUser;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    TossPaymentClient tossPaymentClient;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    OrderFacade orderFacade;

    @Test
    void 주문을_생성하면_결제_대기_상태의_주문을_반환한다() {
        User user = newUser();
        Product product = newProduct();
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(1L, 2)));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(orderRepository.save(ArgumentMatchers.any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderFacade.placeOrder(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(product.getStock()).isEqualTo(10);
    }

    @Test
    void 존재하지_않는_사용자로_주문하면_예외가_발생한다() {
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(1L, 2)));

        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderFacade.placeOrder(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_상품으로_주문하면_예외가_발생한다() {
        User user = newUser();
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(999L, 2)));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderFacade.placeOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 재고보다_많은_수량을_주문하면_예외가_발생한다() {
        User user = newUser();
        Product product = newProduct();
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(1L, 100)));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        assertThatThrownBy(() -> orderFacade.placeOrder(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    void 결제_승인에_성공하면_주문_상태가_PAID로_변경되고_그_시점에_재고가_차감된다() {
        Product product = newProduct();
        Order order = Order.builder().user(newUser()).build();
        order.addItem(OrderItem.builder().product(product).quantity(2).build());
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key-1", 20000);
        OffsetDateTime approvedAt = OffsetDateTime.now();

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));
        given(tossPaymentClient.confirm("payment-key-1", 1L, 20000))
                .willReturn(new TossPaymentConfirmResponse("payment-key-1", "ORDER-1", "카드", 20000, approvedAt));

        OrderResponse response = orderFacade.confirmPayment(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getStock()).isEqualTo(8);
        verify(eventPublisher).publishEvent(any(OrderPaidEvent.class));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getPaymentKey()).isEqualTo("payment-key-1");
        assertThat(savedPayment.getMethod()).isEqualTo("카드");
        assertThat(savedPayment.getAmount()).isEqualTo(20000);
        assertThat(savedPayment.getApprovedAt()).isEqualTo(approvedAt.toLocalDateTime());
    }

    @Test
    void 결제_승인_시점에_재고가_부족하면_예외가_발생하고_주문_상태는_그대로다() {
        Product product = newProduct();
        Order order = Order.builder().user(newUser()).build();
        order.addItem(OrderItem.builder().product(product).quantity(2).build());
        product.decreaseStock(9);
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key-1", 20000);

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderFacade.confirmPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void 결제_금액이_주문_금액과_다르면_예외가_발생하고_Toss_승인을_호출하지_않는다() {
        Order order = Order.builder().user(newUser()).build();
        order.addItem(OrderItem.builder().product(newProduct()).quantity(2).build());
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key-1", 100);

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderFacade.confirmPayment(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.PAYMENT_AMOUNT_MISMATCH);

        verifyNoInteractions(tossPaymentClient);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void 존재하지_않는_주문의_결제를_승인하면_예외가_발생한다() {
        PaymentConfirmRequest request = new PaymentConfirmRequest("payment-key-1", 20000);

        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderFacade.confirmPayment(999L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 결제완료_웹훅을_받으면_주문_상태가_PAID로_변경된다() {
        Order order = Order.builder().user(newUser()).build();
        order.addItem(OrderItem.builder().product(newProduct()).quantity(2).build());
        TossWebhookRequest request = new TossWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                new TossWebhookRequest.Data("payment-key-1", "ORDER-1", "DONE")
        );

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderFacade.handlePaymentWebhook(request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(eventPublisher).publishEvent(any(OrderPaidEvent.class));
    }

    @Test
    void 이미_PAID인_주문에_같은_웹훅이_다시_와도_예외_없이_무시한다() {
        Order order = Order.builder().user(newUser()).build();
        order.addItem(OrderItem.builder().product(newProduct()).quantity(2).build());
        order.pay();
        TossWebhookRequest request = new TossWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                new TossWebhookRequest.Data("payment-key-1", "ORDER-1", "DONE")
        );

        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderFacade.handlePaymentWebhook(request);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(eventPublisher);
    }
}
