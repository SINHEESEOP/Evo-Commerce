package com.evo.commerce.domain.order.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderMapper;
import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.order.domain.OrderStatus;
import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossPaymentClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse placeOrder(Long userId, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Order order = Order.builder().user(user).build();

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

            product.validateStockAvailable(itemRequest.quantity());

            order.addItem(OrderItem.builder()
                    .product(product)
                    .quantity(itemRequest.quantity())
                    .build());
        }

        Order saved = orderRepository.save(order);

        return OrderMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return OrderMapper.toResponse(findOrderOrThrow(orderId));
    }

    @Transactional
    public OrderResponse confirmPayment(Long orderId, PaymentConfirmRequest request) {
        Order order = findOrderOrThrow(orderId);

        if (order.calculateTotalAmount() != request.amount()) {
            throw new BusinessException(OrderErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        TossPaymentConfirmResponse tossResponse = tossPaymentClient.confirm(request.paymentKey(), orderId, request.amount());

        markOrderAsPaid(order, tossResponse.paymentKey(), tossResponse.method(), tossResponse.totalAmount(), tossResponse.approvedAt());

        return OrderMapper.toResponse(order);
    }

    @Transactional
    public void handlePaymentWebhook(TossWebhookRequest request) {
        Order order = findOrderOrThrow(TossPaymentClient.parseOrderId(request.data().orderId()));

        if (!"DONE".equals(request.data().status())) {
            return;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        TossWebhookRequest.Data data = request.data();
        markOrderAsPaid(order, data.paymentKey(), data.method(), data.totalAmount(), data.approvedAt());
    }


    // -------------------------------------- 재사용 유틸 함수 ----------------------------------------


    private void markOrderAsPaid(Order order, String paymentKey, String method, int amount, OffsetDateTime approvedAt) {
        decreaseStockForItems(order);
        order.pay();

        paymentRepository.save(Payment.builder()
                .order(order)
                .paymentKey(paymentKey)
                .method(method)
                .amount(amount)
                .approvedAt(approvedAt.toLocalDateTime())
                .build());

        OrderPaidEvent event = new OrderPaidEvent(order.getId(), order.getUser().getId());
        outboxEventRepository.save(OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload(toPayload(event))
                .status(OutboxEventStatus.PENDING)
                .build());
    }

    private String toPayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화에 실패했습니다.", e);
        }
    }

    private void decreaseStockForItems(Order order) {
        for (OrderItem orderItem : order.getOrderItems()) {
            orderItem.getProduct().decreaseStock(orderItem.getQuantity());
        }
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
