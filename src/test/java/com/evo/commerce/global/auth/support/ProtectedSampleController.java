package com.evo.commerce.global.auth.support;

import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ProtectedSampleController {

    @GetMapping("/api/sample/me")
    public ApiResponse<Long> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);

        log.info("User ID: {}", userId);
        return ApiResponse.success(userId);
    }
}
