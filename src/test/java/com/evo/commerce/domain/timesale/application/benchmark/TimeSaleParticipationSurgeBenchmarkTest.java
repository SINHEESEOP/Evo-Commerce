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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TimeSaleParticipationScaleBenchmarkTest(정원과 동시 요청자 수가 같은 균형 경쟁)와 달리,
 * 정원(100명)은 그대로 두고 지원자만 10배(1,000명)로 늘려 실제 인기 타임세일의 "선착순 마감"
 * 수요 폭증 상황을 재현한다. HikariCP maximum-pool-size는 기본값(10)을 그대로 둔 채로 측정한다.
 */
@SpringBootTest
class TimeSaleParticipationSurgeBenchmarkTest {

    private static final int PARTICIPANT_LIMIT = 100;
    private static final int CONCURRENT_USERS = 1000;

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

    @Test
    void 낙관적_락_재시도_방식은_지원자가_정원보다_훨씬_많아도_정원까지는_모두_성공한다() throws InterruptedException {
        ParticipationLoadRunner.Result result = runScenario(optimisticLockRetryParticipationService::participate);

        assertThat(result.successCount()).isEqualTo(PARTICIPANT_LIMIT);
    }

    @Test
    void Redis_원자적_연산_방식은_지원자가_정원보다_훨씬_많아도_정원까지는_모두_성공한다() throws InterruptedException {
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
                        .email("timesale-surge-" + System.nanoTime() + "-" + i + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("테스터" + i)
                        .role(UserRole.USER)
                        .build()).getId())
                .toList();

        ParticipationLoadRunner.Result result = ParticipationLoadRunner.run(userIds, eventId, participate);

        await().atMost(Duration.ofSeconds(90)).untilAsserted(() ->
                assertThat(timeSaleParticipationRepository.countByTimeSaleEvent(event)).isEqualTo(result.successCount()));

        return result;
    }
}
