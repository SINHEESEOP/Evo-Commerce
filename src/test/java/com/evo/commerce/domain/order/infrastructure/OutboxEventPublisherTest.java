package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxEventPublisherTest {

    @Autowired
    OutboxEventPublisher outboxEventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    private Long outboxEventId;

    @AfterEach
    void cleanUp() {
        if (outboxEventId != null) {
            outboxEventRepository.deleteById(outboxEventId);
        }
    }

    @Test
    void 대기중인_아웃박스_이벤트를_처리하면_발행_완료_상태로_바뀐다() {
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":1,\"userId\":1}")
                .status(OutboxEventStatus.PENDING)
                .build());
        outboxEventId = event.getId();

        outboxEventPublisher.publishPendingEvents();

        OutboxEvent reloaded = outboxEventRepository.findById(outboxEventId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }
}
