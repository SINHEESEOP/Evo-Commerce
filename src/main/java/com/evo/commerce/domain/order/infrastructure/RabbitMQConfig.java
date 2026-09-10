package com.evo.commerce.domain.order.infrastructure;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String ORDER_EXCHANGE = "evo.order.exchange";
    public static final String ORDER_PAID_QUEUE = "order.paid.queue";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    public static final String ORDER_DEAD_LETTER_EXCHANGE = "evo.order.exchange.dlx";
    public static final String ORDER_PAID_DEAD_LETTER_QUEUE = "order.paid.queue.dlq";
    public static final String ORDER_PAID_DEAD_LETTER_ROUTING_KEY = "order.paid.dead";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(ORDER_PAID_QUEUE)
                .withArgument("x-dead-letter-exchange", ORDER_DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ORDER_PAID_DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(orderExchange()).with(ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue orderPaidDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_PAID_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding orderPaidDeadLetterBinding() {
        return BindingBuilder.bind(orderPaidDeadLetterQueue()).to(orderDeadLetterExchange()).with(ORDER_PAID_DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned ->
                log.warn("메시지가 어떤 큐로도 라우팅되지 못해 브로커가 반려했습니다. exchange={}, routingKey={}, replyCode={}, replyText={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        return rabbitTemplate;
    }
}
