package com.evo.commerce.domain.order.presentation;

import com.evo.commerce.domain.order.application.OrderFacade;
import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.domain.order.dto.PaymentConfirmRequest;
import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderResponse> placeOrder(HttpServletRequest request, @Valid @RequestBody OrderCreateRequest createRequest) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        return ApiResponse.success(orderFacade.placeOrder(userId, createRequest));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderFacade.getOrder(orderId));
    }

    @PostMapping("/{orderId}/payments/confirm")
    public ApiResponse<OrderResponse> confirmPayment(@PathVariable Long orderId, @Valid @RequestBody PaymentConfirmRequest request) {
        return ApiResponse.success(orderFacade.confirmPayment(orderId, request));
    }
}
