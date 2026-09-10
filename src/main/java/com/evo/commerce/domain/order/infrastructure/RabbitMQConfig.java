package com.evo.commerce.domain.order.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "evo.order.exchange";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(ORDER_PAID_QUEUE);
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(orderExchange()).with(ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
