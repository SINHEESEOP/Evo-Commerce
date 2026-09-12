package com.evo.commerce.domain.timesale.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeSaleRabbitMQConfig {

    public static final String PARTICIPATION_EXCHANGE = "evo.time-sale.exchange";
    public static final String PARTICIPATION_REQUESTED_QUEUE = "time-sale.participation-requested.queue";
    public static final String PARTICIPATION_REQUESTED_ROUTING_KEY = "time-sale.participation-requested";

    public static final String PARTICIPATION_DEAD_LETTER_EXCHANGE = "evo.time-sale.exchange.dlx";
    public static final String PARTICIPATION_REQUESTED_DEAD_LETTER_QUEUE = "time-sale.participation-requested.queue.dlq";
    public static final String PARTICIPATION_REQUESTED_DEAD_LETTER_ROUTING_KEY = "time-sale.participation-requested.dead";

    @Bean
    public DirectExchange timeSaleExchange() {
        return new DirectExchange(PARTICIPATION_EXCHANGE);
    }

    @Bean
    public Queue participationRequestedQueue() {
        return QueueBuilder.durable(PARTICIPATION_REQUESTED_QUEUE)
                .withArgument("x-dead-letter-exchange", PARTICIPATION_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PARTICIPATION_REQUESTED_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding participationRequestedBinding() {
        return BindingBuilder.bind(participationRequestedQueue()).to(timeSaleExchange()).with(PARTICIPATION_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public DirectExchange timeSaleDeadLetterExchange() {
        return new DirectExchange(PARTICIPATION_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue participationRequestedDeadLetterQueue() {
        return QueueBuilder.durable(PARTICIPATION_REQUESTED_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding participationRequestedDeadLetterBinding() {
        return BindingBuilder.bind(participationRequestedDeadLetterQueue()).to(timeSaleDeadLetterExchange()).with(PARTICIPATION_REQUESTED_DEAD_LETTER_ROUTING_KEY);
    }
}
