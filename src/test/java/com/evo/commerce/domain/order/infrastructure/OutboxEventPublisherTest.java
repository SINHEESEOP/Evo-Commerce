package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.notification.domain.NotificationRepository;
import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.domain.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxEventPublisherTest {

    @Autowired
    OutboxEventPublisher outboxEventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    private Long outboxEventId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        if (outboxEventId != null) {
            outboxEventRepository.deleteById(outboxEventId);
        }
        if (userId != null) {
            notificationRepository.deleteByUser_Id(userId);
            userRepository.deleteById(userId);
        }
    }

    @Test
    void 발행에_성공한_이벤트는_발행_완료_상태로_바뀐다() {
        User user = userRepository.save(User.builder()
                .email("outbox-success-" + System.nanoTime() + "@evo-commerce.com")
                .password("plain1234!")
                .name("테스터")
                .role(UserRole.USER)
                .build());
        userId = user.getId();

        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":1,\"userId\":" + userId + "}")
                .status(OutboxEventStatus.PENDING)
                .build());
        outboxEventId = event.getId();

        outboxEventPublisher.publishPendingEvents();

        OutboxEventStatus status = reloadViaMaster();
        assertThat(status).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void 발행에_실패한_이벤트는_대기_상태로_남아_다음_스케줄에서_재시도된다() {
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":1,\"userId\":999999}")
                .status(OutboxEventStatus.PENDING)
                .build());
        outboxEventId = event.getId();

        outboxEventPublisher.publishPendingEvents();

        OutboxEventStatus status = reloadViaMaster();
        assertThat(status).isEqualTo(OutboxEventStatus.PENDING);
    }

    /**
     * findById()는 readOnly 트랜잭션으로 라우팅되어 슬레이브에서 읽는다.
     * 방금 마스터에 커밋한 값을 곧바로 검증할 때는 복제 지연 때문에 슬레이브에
     * 아직 반영되지 않았을 수 있으므로, 쓰기 트랜잭션으로 감싸 마스터에서 직접 읽는다.
     */
    private OutboxEventStatus reloadViaMaster() {
        return transactionTemplate.execute(status ->
                outboxEventRepository.findById(outboxEventId).orElseThrow().getStatus());
    }
}
