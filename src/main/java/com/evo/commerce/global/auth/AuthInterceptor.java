package com.evo.commerce.global.auth;

import com.evo.commerce.global.exception.AuthErrorCode;
import com.evo.commerce.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE) == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED);
        }
        return true;
    }
}
