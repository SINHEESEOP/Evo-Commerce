package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
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
        saved.pay();

        return OrderMapper.toResponse(saved);
    }
}
