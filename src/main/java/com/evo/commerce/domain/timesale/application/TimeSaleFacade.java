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
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeSaleFacade {

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final TimeSaleParticipationRepository timeSaleParticipationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

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
        return TimeSaleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TimeSaleEventResponse> getEvents() {
        return timeSaleEventRepository.findAll().stream()
                .map(TimeSaleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TimeSaleEventResponse getEvent(Long eventId) {
        return TimeSaleMapper.toResponse(findEventOrThrow(eventId));
    }

    /**
     * {@code @Retryable}과 {@code @Transactional}을 같은 메서드에 함께 걸면, 두 번째 시도부터도
     * 첫 시도 때 이미 실패한(플러시 시점에 버전 충돌이 확정된) 영속성 컨텍스트를 그대로 재사용하게 되어
     * 재시도가 무의미해진다. {@link TransactionTemplate}으로 매 시도마다 새 트랜잭션을 여는 이유다.
     */
    @Retryable(
            retryFor = ConcurrencyFailureException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 500)
    )
    public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
        return transactionTemplate.execute(status -> participateInNewTransaction(userId, eventId));
    }

    @Recover
    public TimeSaleParticipationResponse recoverFromConcurrencyFailure(
            ConcurrencyFailureException e, Long userId, Long eventId) {
        throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
    }

    private TimeSaleParticipationResponse participateInNewTransaction(Long userId, Long eventId) {
        TimeSaleEvent event = findEventOrThrow(eventId);
        event.validateInProgress(LocalDateTime.now());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        if (timeSaleParticipationRepository.existsByTimeSaleEventAndUser(event, user)) {
            throw new BusinessException(TimeSaleErrorCode.ALREADY_PARTICIPATED);
        }

        event.increaseParticipant();

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
