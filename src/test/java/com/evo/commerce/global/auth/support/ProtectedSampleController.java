package com.evo.commerce.global.auth.support;

import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtectedSampleController {

    @GetMapping("/api/sample/me")
    public ApiResponse<Long> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        return ApiResponse.success(userId);
    }
}
