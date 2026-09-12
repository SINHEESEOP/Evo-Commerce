package com.evo.commerce.domain.timesale.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRequestedEvent;
import com.evo.commerce.domain.timesale.infrastructure.TimeSaleRabbitMQConfig;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TimeSaleParticipationMessageListener {

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final TimeSaleParticipationRepository timeSaleParticipationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @RabbitListener(queues = TimeSaleRabbitMQConfig.PARTICIPATION_REQUESTED_QUEUE)
    @Transactional
    public void handleParticipationRequested(TimeSaleParticipationRequestedEvent event) {
        TimeSaleEvent timeSaleEvent = timeSaleEventRepository.findById(event.eventId())
                .orElseThrow(() -> new BusinessException(TimeSaleErrorCode.TIME_SALE_EVENT_NOT_FOUND));
        User user = userRepository.findById(event.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        timeSaleEvent.increaseParticipant();

        Order order = Order.builder().user(user).build();
        order.addItem(OrderItem.builder()
                .product(timeSaleEvent.getProduct())
                .quantity(1)
                .unitPrice(timeSaleEvent.getDiscountPrice())
                .build());

        Order savedOrder = orderRepository.save(order);

        timeSaleParticipationRepository.save(TimeSaleParticipation.builder()
                .timeSaleEvent(timeSaleEvent)
                .user(user)
                .order(savedOrder)
                .build());
    }
}
