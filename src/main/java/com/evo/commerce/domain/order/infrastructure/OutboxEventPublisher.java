package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.domain.OrderPaidEvent;
import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRequestedEvent;
import com.evo.commerce.domain.timesale.infrastructure.TimeSaleRabbitMQConfig;
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

    private static final long PUBLISHER_CONFIRM_TIMEOUT_MILLIS = 5000;

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
            switch (event.getEventType()) {
                case "ORDER_PAID" -> dispatchOrderPaid(event);
                case "TIME_SALE_PARTICIPATION_REQUESTED" -> dispatchParticipationRequested(event);
                default -> log.warn("처리할 수 없는 아웃박스 이벤트 타입입니다. eventType={}, eventId={}", event.getEventType(), event.getId());
            }
        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행에 실패했습니다. 다음 스케줄에서 재시도합니다. eventId={}", event.getId(), e);
        }
    }

    private void dispatchOrderPaid(OutboxEvent event) throws Exception {
        OrderPaidEvent payload = objectMapper.readValue(event.getPayload(), OrderPaidEvent.class);
        publishAndAwaitBrokerConfirm(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_PAID_ROUTING_KEY, payload);
        markAsSent(event);
    }

    private void dispatchParticipationRequested(OutboxEvent event) throws Exception {
        TimeSaleParticipationRequestedEvent payload = objectMapper.readValue(event.getPayload(), TimeSaleParticipationRequestedEvent.class);
        publishAndAwaitBrokerConfirm(TimeSaleRabbitMQConfig.PARTICIPATION_EXCHANGE, TimeSaleRabbitMQConfig.PARTICIPATION_REQUESTED_ROUTING_KEY, payload);
        markAsSent(event);
    }

    private void markAsSent(OutboxEvent event) {
        transactionTemplate.executeWithoutResult(status -> {
            event.markAsSent();
            outboxEventRepository.save(event);
        });
    }

    private void publishAndAwaitBrokerConfirm(String exchange, String routingKey, Object payload) {
        rabbitTemplate.invoke(operations -> {
            operations.convertAndSend(exchange, routingKey, payload);
            operations.waitForConfirmsOrDie(PUBLISHER_CONFIRM_TIMEOUT_MILLIS);
            return null;
        });
    }
}
