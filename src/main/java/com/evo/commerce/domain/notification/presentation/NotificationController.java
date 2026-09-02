package com.evo.commerce.domain.notification.presentation;

import com.evo.commerce.domain.notification.domain.SseEmitterRegistry;
import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        return sseEmitterRegistry.register(userId);
    }
}
