package com.evo.commerce.domain.notification;

import com.evo.commerce.domain.order.OrderPaidEvent;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.domain.user.UserRole;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    NotificationRepository notificationRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    SseEmitterRegistry sseEmitterRegistry;

    @InjectMocks
    NotificationEventListener notificationEventListener;

    @Test
    void 결제완료_이벤트를_받으면_알림을_저장한다() throws Exception {
        User user = User.builder()
                .email("tester@evo-commerce.com")
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();
        OrderPaidEvent event = new OrderPaidEvent(1L, 1L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(sseEmitterRegistry.findByUserId(1L)).willReturn(List.of());

        notificationEventListener.handleOrderPaid(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getMessage()).contains("1");
    }

    @Test
    void 존재하지_않는_사용자의_이벤트를_받으면_예외가_발생한다() {
        OrderPaidEvent event = new OrderPaidEvent(1L, 999L);

        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationEventListener.handleOrderPaid(event))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }
}
