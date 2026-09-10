package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    OutboxEventRepository outboxEventRepository;

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    TransactionTemplate transactionTemplate;

    OutboxEventPublisher outboxEventPublisher;

    @BeforeEach
    void setUp() {
        outboxEventPublisher = new OutboxEventPublisher(outboxEventRepository, rabbitTemplate, new ObjectMapper(), transactionTemplate);
    }

    @Test
    void 브로커가_발행을_컨펌하면_이벤트는_발행_완료_상태로_바뀐다() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":1,\"userId\":1}")
                .status(OutboxEventStatus.PENDING)
                .build();
        given(outboxEventRepository.findByStatus(OutboxEventStatus.PENDING)).willReturn(List.of(event));
        given(rabbitTemplate.invoke(any())).willReturn(null);
        runTransactionSynchronously();

        outboxEventPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 브로커_컨펌을_받지_못하면_이벤트는_대기_상태로_남아_다음_스케줄에서_재시도된다() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("ORDER_PAID")
                .payload("{\"orderId\":1,\"userId\":1}")
                .status(OutboxEventStatus.PENDING)
                .build();
        given(outboxEventRepository.findByStatus(OutboxEventStatus.PENDING)).willReturn(List.of(event));
        given(rabbitTemplate.invoke(any())).willThrow(new AmqpException("퍼블리셔 컨펌 타임아웃"));

        outboxEventPublisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        verify(outboxEventRepository, never()).save(event);
    }

    @SuppressWarnings("unchecked")
    private void runTransactionSynchronously() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
