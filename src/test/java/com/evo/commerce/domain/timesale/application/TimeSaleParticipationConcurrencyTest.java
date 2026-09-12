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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class TimeSaleParticipationConcurrencyTest {

    private static final int PARTICIPANT_LIMIT = 3;
    private static final int CONCURRENT_USERS = 10;

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
    void 동시에_여러_명이_참여해도_선착순_인원_제한을_초과하지_않는다() throws InterruptedException {
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
                        .email("timesale-concurrency-" + System.nanoTime() + "-" + i + "@evo-commerce.com")
                        .password("plain1234!")
                        .name("테스터" + i)
                        .role(UserRole.USER)
                        .build()).getId())
                .toList();

        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_USERS);
        AtomicInteger successCount = new AtomicInteger();

        for (Long userId : userIds) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    timeSaleFacade.participate(userId, eventId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 인원 마감으로 실패하는 경우는 정상 흐름이므로 별도 처리하지 않는다.
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(PARTICIPANT_LIMIT);

        // Order/Participation 저장은 아웃박스+RabbitMQ를 통해 비동기로 처리되므로,
        // 참여 응답이 돌아온 직후가 아니라 최종적으로(eventually) 정원만큼 쌓일 때까지 기다려야 한다.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(timeSaleParticipationRepository.countByTimeSaleEvent(event)).isEqualTo(PARTICIPANT_LIMIT));
    }
}
