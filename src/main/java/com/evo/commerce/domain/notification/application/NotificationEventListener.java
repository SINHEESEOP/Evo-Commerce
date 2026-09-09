package com.evo.commerce.domain.notification.application;

import com.evo.commerce.domain.notification.domain.Notification;
import com.evo.commerce.domain.notification.domain.NotificationRepository;
import com.evo.commerce.domain.notification.infrastructure.SseEmitterRegistry;
import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .user(user)
                .message("주문 #" + event.orderId() + " 결제가 완료되었습니다.")
                .build();

        notificationRepository.save(notification);

        for (SseEmitter emitter : sseEmitterRegistry.findByUserId(event.userId())) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification.getMessage()));
            } catch (IOException e) {
                log.warn("SSE 알림 전송에 실패했습니다. userId={}", event.userId(), e);
            }
        }
    }
}
