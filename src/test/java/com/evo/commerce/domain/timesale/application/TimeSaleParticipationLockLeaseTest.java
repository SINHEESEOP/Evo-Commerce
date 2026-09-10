package com.evo.commerce.domain.timesale.application;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.domain.user.domain.UserRole;
import com.evo.commerce.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class TimeSaleParticipationLockLeaseTest {

    private static final int PARTICIPANT_LIMIT = 5;
    private static final long SLOW_TRANSACTION_MILLIS = 4_000;
    private static final ThreadLocal<Boolean> SLOW_TRANSACTION = ThreadLocal.withInitial(() -> false);

    @Autowired
    TimeSaleFacade timeSaleFacade;

    @Autowired
    TimeSaleEventRepository timeSaleEventRepository;

    @Autowired
    TimeSaleParticipationRepository timeSaleParticipationRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    OrderRepository orderRepository;

    @MockitoSpyBean
    TransactionTemplate transactionTemplate;

    private Long eventId;
    private Long productId;
    private List<Long> userIds;

    @AfterEach
    void cleanUp() {
        List<TimeSaleParticipation> participations = timeSaleParticipationRepository.findAll();
        List<Order> orders = participations.stream().map(TimeSaleParticipation::getOrder).toList();
        timeSaleParticipationRepository.deleteAll(participations);
        orderRepository.deleteAll(orders);
        if (eventId != null) {
            timeSaleEventRepository.deleteById(eventId);
        }
        if (productId != null) {
            productRepository.deleteById(productId);
        }
        if (userIds != null) {
            userRepository.deleteAllById(userIds);
        }
    }

    @Test
    void 앞선_참여자의_처리가_오래_걸려도_뒤이은_참여자는_락이_풀릴_때까지_대기한다() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        Product product = productRepository.save(newProduct());
        productId = product.getId();

        TimeSaleEvent event = timeSaleEventRepository.save(TimeSaleEvent.builder()
                .product(product)
                .discountPrice(59000)
                .participantLimit(PARTICIPANT_LIMIT)
                .startAt(now.minusMinutes(10))
                .endAt(now.plusMinutes(10))
                .build());
        eventId = event.getId();

        Long slowUserId = userRepository.save(User.builder()
                        .email("timesale-lease-slow-" + System.nanoTime() + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("느린참여자")
                        .role(UserRole.USER)
                        .build())
                .getId();
        Long fastUserId = userRepository.save(User.builder()
                        .email("timesale-lease-fast-" + System.nanoTime() + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("빠른참여자")
                        .role(UserRole.USER)
                        .build())
                .getId();
        userIds = List.of(slowUserId, fastUserId);

        // 현재 스레드가 slowUserId를 처리 중일 때만 트랜잭션 처리를 지연시켜,
        // Redisson 락의 leaseTime(3000ms)보다 오래 걸리는 트랜잭션 상황을 흉내낸다.
        doAnswer(invocation -> {
            if (Boolean.TRUE.equals(SLOW_TRANSACTION.get())) {
                Thread.sleep(SLOW_TRANSACTION_MILLIS);
            }
            return invocation.callRealMethod();
        }).when(transactionTemplate).execute(any());

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        var slowFuture = executorService.submit(() -> {
            SLOW_TRANSACTION.set(true);
            return timeSaleFacade.participate(slowUserId, eventId);
        });

        // slowUserId가 락을 획득하고 지연 구간(트랜잭션 처리)에 진입할 때까지 대기한다.
        Thread.sleep(500);

        // 아직 slowUserId의 트랜잭션이 끝나지 않았으므로(락을 보유 중이므로),
        // fastUserId의 참여 요청은 락을 획득하지 못하고 일시적으로 실패해야 한다.
        Thread.sleep(2_800);
        assertThat(slowFuture.isDone()).isFalse();
        assertThatThrownBy(() -> timeSaleFacade.participate(fastUserId, eventId))
                .isInstanceOf(BusinessException.class);

        slowFuture.get();
        executorService.shutdown();
    }
}
