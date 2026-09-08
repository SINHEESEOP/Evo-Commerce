package com.evo.commerce.domain.timesale.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleMapper;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventCreateRequest;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;
import com.evo.commerce.domain.timesale.dto.TimeSaleParticipationResponse;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeSaleFacade {

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final TimeSaleParticipationRepository timeSaleParticipationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public TimeSaleEventResponse createEvent(TimeSaleEventCreateRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        TimeSaleEvent event = TimeSaleEvent.builder()
                .product(product)
                .discountPrice(request.discountPrice())
                .participantLimit(request.participantLimit())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .build();

        TimeSaleEvent saved = timeSaleEventRepository.save(event);
        return TimeSaleMapper.toResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<TimeSaleEventResponse> getEvents() {
        return timeSaleEventRepository.findAll().stream()
                .map(event -> TimeSaleMapper.toResponse(event, timeSaleParticipationRepository.countByTimeSaleEvent(event)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeSaleEventResponse getEvent(Long eventId) {
        TimeSaleEvent event = findEventOrThrow(eventId);
        return TimeSaleMapper.toResponse(event, timeSaleParticipationRepository.countByTimeSaleEvent(event));
    }

    @Transactional
    public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
        TimeSaleEvent event = findEventOrThrow(eventId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (timeSaleParticipationRepository.existsByTimeSaleEventAndUser(event, user)) {
            throw new BusinessException(TimeSaleErrorCode.ALREADY_PARTICIPATED);
        }

        long currentParticipants = timeSaleParticipationRepository.countByTimeSaleEvent(event);
        if (currentParticipants >= event.getParticipantLimit()) {
            throw new BusinessException(TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED);
        }

        Order order = Order.builder().user(user).build();
        order.addItem(OrderItem.builder()
                .product(event.getProduct())
                .quantity(1)
                .unitPrice(event.getDiscountPrice())
                .build());

        Order savedOrder = orderRepository.save(order);

        TimeSaleParticipation participation = timeSaleParticipationRepository.save(TimeSaleParticipation.builder()
                .timeSaleEvent(event)
                .user(user)
                .order(savedOrder)
                .build());

        return new TimeSaleParticipationResponse(participation.getId(), savedOrder.getId(), event.getId());
    }

    private TimeSaleEvent findEventOrThrow(Long eventId) {
        return timeSaleEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(TimeSaleErrorCode.TIME_SALE_EVENT_NOT_FOUND));
    }
}
