package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                OrderPaidEvent payload = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
                eventPublisher.publishEvent(payload);
            } catch (Exception e) {
                log.error("아웃박스 이벤트 발행 중 오류가 발생했습니다. eventId={}", event.getId(), e);
            } finally {
                event.markAsSent();
            }
        }
    }
}
