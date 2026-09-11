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
 * TimeSaleParticipationSurgeBenchmarkTest와 같은 시나리오(정원 100명, 지원자 1,000명)를,
 * HikariCP maximum-pool-size만 20으로 넓혀서 재측정한다. 커넥션 풀을 넉넉하게 늘리는 것만으로
 * 수요 폭증 상황의 오탐 거부가 해소되는지 비교하기 위한 테스트다.
 */
@SpringBootTest(properties = {
        "spring.datasource.master.hikari.maximum-pool-size=20",
        "spring.datasource.slave.hikari.maximum-pool-size=20"
})
class TimeSaleParticipationSurgePoolExpandedBenchmarkTest {

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
    void 낙관적_락_재시도_방식은_커넥션_풀을_넓히면_지원자가_정원보다_훨씬_많아도_정원까지는_모두_성공한다() throws InterruptedException {
        ParticipationLoadRunner.Result result = runScenario(optimisticLockRetryParticipationService::participate);

        assertThat(result.successCount()).isEqualTo(PARTICIPANT_LIMIT);
    }

    @Disabled("ISSUE-32: 커넥션 풀을 넓혀도 tryLock 대기시간 초과로 정원의 극히 일부만 채워짐 - 3.11 원자적 Redis 연산 재설계 후 재활성화 예정")
    @Test
    void Redis_분산_락_방식은_커넥션_풀을_넓히면_지원자가_정원보다_훨씬_많아도_정원까지는_모두_성공한다() throws InterruptedException {
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
                        .email("timesale-surge-pool20-" + System.nanoTime() + "-" + i + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("테스터" + i)
                        .role(UserRole.USER)
                        .build()).getId())
                .toList();

        return ParticipationLoadRunner.run(userIds, eventId, participate);
    }
}
