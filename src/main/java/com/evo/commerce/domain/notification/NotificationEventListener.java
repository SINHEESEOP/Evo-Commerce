package com.evo.commerce.domain.notification;

import com.evo.commerce.domain.order.OrderPaidEvent;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) {
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        Notification notification = Notification.builder()
                .user(user)
                .message("주문 #" + event.orderId() + " 결제가 완료되었습니다.")
                .build();

        notificationRepository.save(notification);
    }
}
