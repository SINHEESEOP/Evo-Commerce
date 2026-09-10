package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            dispatch(event);
        }
    }

    private void dispatch(OutboxEvent event) {
        try {
            OrderPaidEvent payload = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_PAID_ROUTING_KEY, payload);
            transactionTemplate.executeWithoutResult(status -> {
                event.markAsSent();
                outboxEventRepository.save(event);
            });
        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행에 실패했습니다. 다음 스케줄에서 재시도합니다. eventId={}", event.getId(), e);
        }
    }
}
