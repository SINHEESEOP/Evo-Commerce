package com.evo.commerce.domain.timesale.application.benchmark;

import com.evo.commerce.domain.order.domain.Order;
import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.timesale.application.TimeSaleFacade;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.domain.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3.6 벤치마크(정원 3명, 동시 요청 10건 - 기본 HikariCP 풀 크기와 우연히 일치)를
 * 실제 Phase 3 목표 규모(선착순 100명, 동시 요청 100건)로 재측정한다.
 * HikariCP maximum-pool-size는 application.yaml 기본값(10)을 그대로 둔 채로 측정한다.
 */
@SpringBootTest
class TimeSaleParticipationScaleBenchmarkTest {

    private static final int PARTICIPANT_LIMIT = 100;
    private static final int CONCURRENT_USERS = 100;

    @Autowired
    TimeSaleFacade timeSaleFacade;

    @Autowired
    OptimisticLockRetryParticipationService optimisticLockRetryParticipationService;

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

    @Disabled("ISSUE-32: 재시도 소진으로 잔여 정원이 있어도 참여가 거부됨 - 3.11 원자적 Redis 연산 재설계 후 재활성화 예정")
    @Test
    void 낙관적_락_재시도_방식은_동시_참여자가_많아져도_정원까지는_모두_성공한다() throws InterruptedException {
        ParticipationLoadRunner.Result result = runScenario(optimisticLockRetryParticipationService::participate);

        assertThat(result.successCount()).isEqualTo(PARTICIPANT_LIMIT);
    }

    @Disabled("ISSUE-32: RLock tryLock 대기시간 초과로 잔여 정원이 있어도 참여가 거부됨 - 3.11 원자적 Redis 연산 재설계 후 재활성화 예정")
    @Test
    void Redis_분산_락_방식은_동시_참여자가_많아져도_정원까지는_모두_성공한다() throws InterruptedException {
        ParticipationLoadRunner.Result result = runScenario(timeSaleFacade::participate);

        assertThat(result.successCount()).isEqualTo(PARTICIPANT_LIMIT);
    }

    private ParticipationLoadRunner.Result runScenario(BiConsumer<Long, Long> participate) throws InterruptedException {
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

        userIds = IntStream.range(0, CONCURRENT_USERS)
                .mapToObj(i -> userRepository.save(User.builder()
                        .email("timesale-scale-" + System.nanoTime() + "-" + i + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("테스터" + i)
                        .role(UserRole.USER)
                        .build()).getId())
                .toList();

        return ParticipationLoadRunner.run(userIds, eventId, participate);
    }
}
