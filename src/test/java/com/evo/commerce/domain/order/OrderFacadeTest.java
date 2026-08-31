package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderItemRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.domain.user.UserRole;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    OrderRepository orderRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    OrderFacade orderFacade;

    private User newUser() {
        return User.builder()
                .email("tester@evo-commerce.com")
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();
    }

    private Product newProduct() {
        return Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();
    }

    @Test
    void 주문을_생성하면_결제까지_완료된_주문을_반환한다() {
        User user = newUser();
        Product product = newProduct();
        OrderCreateRequest request = new OrderCreateRequest(List.of(new OrderItemRequest(1L, 2)));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(orderRepository.save(ArgumentMatchers.any(Order.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderFacade.placeOrder(1L, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(product.getStock()).isEqualTo(8);
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
}
