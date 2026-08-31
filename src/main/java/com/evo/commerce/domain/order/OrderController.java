package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.OrderCreateRequest;
import com.evo.commerce.domain.order.dto.OrderResponse;
import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
