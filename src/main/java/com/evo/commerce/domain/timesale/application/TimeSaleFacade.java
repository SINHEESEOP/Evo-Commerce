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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TimeSaleFacade {

    private static final String PARTICIPATION_LOCK_KEY_PREFIX = "time-sale:participation-lock:";
    private static final long LOCK_WAIT_MILLIS = 200;
    private static final long LOCK_LEASE_MILLIS = 3_000;

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final TimeSaleParticipationRepository timeSaleParticipationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final RedissonClient redissonClient;

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
     * 락 해제는 반드시 트랜잭션 커밋 이후여야 한다 — {@code @Transactional}을 이 메서드에 직접 걸면
     * 프록시가 메서드 반환 후에야 커밋하므로, {@code finally}의 {@code unlock()}이 커밋보다 먼저
     * 실행되어 다음 스레드가 아직 반영되지 않은(커밋 전) 값을 읽어버린다. {@link TransactionTemplate}으로
     * 트랜잭션을 명시적으로 열고 그 실행이 끝난(=커밋된) 뒤에 락을 반납하는 이유다.
     */
    public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
        RLock lock = redissonClient.getLock(PARTICIPATION_LOCK_KEY_PREFIX + eventId);

        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_MILLIS, LOCK_LEASE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
        }
        if (!acquired) {
            throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
        }

        try {
            return transactionTemplate.execute(status -> participateInNewTransaction(userId, eventId));
        } finally {
            lock.unlock();
        }
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
