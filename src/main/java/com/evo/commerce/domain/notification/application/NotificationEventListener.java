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
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterRegistry sseEmitterRegistry;

    @Async
    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) throws IOException {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .user(user)
                .message("주문 #" + event.orderId() + " 결제가 완료되었습니다.")
                .build();

        notificationRepository.save(notification);

        for (SseEmitter emitter : sseEmitterRegistry.findByUserId(event.userId())) {
            emitter.send(SseEmitter.event().name("notification").data(notification.getMessage()));
        }
    }
}
