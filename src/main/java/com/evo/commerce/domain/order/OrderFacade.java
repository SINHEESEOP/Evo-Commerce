package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.order.dto.PaymentConfirmRequest;
import com.evo.commerce.domain.order.dto.TossWebhookRequest;
import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.OrderErrorCode;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TossPaymentClient tossPaymentClient;

    @Transactional
    public OrderResponse placeOrder(Long userId, OrderCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Order order = Order.builder().user(user).build();

        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

            product.decreaseStock(itemRequest.quantity());

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

        tossPaymentClient.confirm(request.paymentKey(), orderId, request.amount());

        order.pay();

        return OrderMapper.toResponse(order);
    }

    @Transactional
    public void handlePaymentWebhook(TossWebhookRequest request) {
        Order order = findOrderOrThrow(Long.valueOf(request.data().orderId()));

        if ("DONE".equals(request.data().status())) {
            order.pay();
        }
    }

    private Order findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
