package com.evo.commerce.domain.timesale.application.benchmark;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.TimeSaleErrorCode;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 동시성 참여 벤치마크 테스트들이 공통으로 쓰는 부하 발생 로직을 모아둔 테스트 전용 유틸리티.
 * 참여 성공/정상 마감/일시적 거부(false rejection 포함)를 구분해서 집계한다.
 */
public final class ParticipationLoadRunner {

    private ParticipationLoadRunner() {
    }

    public record Result(int successCount, int limitExceededCount, int temporarilyUnavailableCount, int otherFailureCount, long elapsedMillis) {
    }

    public static Result run(List<Long> userIds, Long eventId, BiConsumer<Long, Long> participate) throws InterruptedException {
        int concurrentUsers = userIds.size();
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        CountDownLatch readyLatch = new CountDownLatch(concurrentUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger limitExceededCount = new AtomicInteger();
        AtomicInteger temporarilyUnavailableCount = new AtomicInteger();
        AtomicInteger otherFailureCount = new AtomicInteger();

        for (Long userId : userIds) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    participate.accept(userId, eventId);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED) {
                        limitExceededCount.incrementAndGet();
                    } else if (e.getErrorCode() == TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE) {
                        temporarilyUnavailableCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    otherFailureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        long elapsedMillis = System.currentTimeMillis() - start;
        executorService.shutdown();

        return new Result(successCount.get(), limitExceededCount.get(), temporarilyUnavailableCount.get(), otherFailureCount.get(), elapsedMillis);
    }
}
