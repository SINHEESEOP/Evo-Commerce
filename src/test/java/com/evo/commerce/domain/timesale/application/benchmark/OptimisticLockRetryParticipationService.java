package com.evo.commerce.domain.timesale.application.benchmark;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderItem;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.timesale.dto.TimeSaleParticipationResponse;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Step 3.6에서 Redis 분산 락으로 완전히 교체되기 전, {@code TimeSaleFacade.participate()}가
 * 실제로 썼던 "낙관적 락 + 재시도" 구현을 그대로 재현한 벤치마크 전용 컴포넌트.
 * 프로덕션 코드(src/main)에는 존재하지 않으며, Step 3.10 재측정 테스트에서만 사용한다.
 */
@Component
@RequiredArgsConstructor
public class OptimisticLockRetryParticipationService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long[] BACKOFF_MILLIS = {50, 100, 200, 400, 500};

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final TimeSaleParticipationRepository timeSaleParticipationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> participateInNewTransaction(userId, eventId));
            } catch (ConcurrencyFailureException e) {
                sleep(BACKOFF_MILLIS[attempt]);
            }
        }
        throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
        }
    }

    private TimeSaleParticipationResponse participateInNewTransaction(Long userId, Long eventId) {
        TimeSaleEvent event = timeSaleEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(TimeSaleErrorCode.TIME_SALE_EVENT_NOT_FOUND));
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
}
