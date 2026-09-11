package com.evo.commerce.domain.timesale.application.benchmark;

import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.TimeSaleErrorCode;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 동시성 참여 벤치마크 테스트들이 공통으로 쓰는 부하 발생 로직을 모아둔 테스트 전용 유틸리티.
 * 참여 성공/정상 마감/일시적 거부(false rejection 포함)를 구분해서 집계하고,
 * 각 결과 유형별 개별 요청 지연 시간(min/avg/p95/max)도 함께 측정한다.
 */
public final class ParticipationLoadRunner {

    private ParticipationLoadRunner() {
    }

    public record LatencyStats(long minMillis, double avgMillis, long p95Millis, long maxMillis) {

        static LatencyStats of(Queue<Long> latenciesNanos) {
            if (latenciesNanos.isEmpty()) {
                return new LatencyStats(0, 0, 0, 0);
            }
            List<Long> sorted = latenciesNanos.stream().sorted().toList();
            int p95Index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1);
            double avgNanos = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
            return new LatencyStats(
                    toMillis(sorted.get(0)),
                    avgNanos / 1_000_000.0,
                    toMillis(sorted.get(p95Index)),
                    toMillis(sorted.get(sorted.size() - 1)));
        }

        private static long toMillis(long nanos) {
            return nanos / 1_000_000;
        }

        @Override
        public String toString() {
            return "min %dms, avg %.1fms, p95 %dms, max %dms".formatted(minMillis, avgMillis, p95Millis, maxMillis);
        }
    }

    public record Result(int successCount, int limitExceededCount, int temporarilyUnavailableCount, int otherFailureCount, long elapsedMillis,
                          LatencyStats successLatency, LatencyStats limitExceededLatency,
                          LatencyStats temporarilyUnavailableLatency, LatencyStats otherFailureLatency) {
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
        Queue<Long> successLatenciesNanos = new ConcurrentLinkedQueue<>();
        Queue<Long> limitExceededLatenciesNanos = new ConcurrentLinkedQueue<>();
        Queue<Long> temporarilyUnavailableLatenciesNanos = new ConcurrentLinkedQueue<>();
        Queue<Long> otherFailureLatenciesNanos = new ConcurrentLinkedQueue<>();

        for (Long userId : userIds) {
            executorService.submit(() -> {
                readyLatch.countDown();
                long taskStart = 0;
                try {
                    startLatch.await();
                    taskStart = System.nanoTime();
                    participate.accept(userId, eventId);
                    successCount.incrementAndGet();
                    successLatenciesNanos.add(System.nanoTime() - taskStart);
                } catch (BusinessException e) {
                    long latencyNanos = System.nanoTime() - taskStart;
                    if (e.getErrorCode() == TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED) {
                        limitExceededCount.incrementAndGet();
                        limitExceededLatenciesNanos.add(latencyNanos);
                    } else if (e.getErrorCode() == TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE) {
                        temporarilyUnavailableCount.incrementAndGet();
                        temporarilyUnavailableLatenciesNanos.add(latencyNanos);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    otherFailureCount.incrementAndGet();
                    otherFailureLatenciesNanos.add(System.nanoTime() - taskStart);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(120, TimeUnit.SECONDS);
        long elapsedMillis = System.currentTimeMillis() - start;
        executorService.shutdown();

        Result result = new Result(successCount.get(), limitExceededCount.get(), temporarilyUnavailableCount.get(), otherFailureCount.get(), elapsedMillis,
                LatencyStats.of(successLatenciesNanos), LatencyStats.of(limitExceededLatenciesNanos),
                LatencyStats.of(temporarilyUnavailableLatenciesNanos), LatencyStats.of(otherFailureLatenciesNanos));

        System.out.printf("""
                [ParticipationLoadRunner] 총 소요 %dms
                  성공(%d건): %s
                  정상마감(%d건): %s
                  오탐거부(%d건): %s
                  기타실패(%d건): %s
                %n""",
                result.elapsedMillis(),
                result.successCount(), result.successLatency(),
                result.limitExceededCount(), result.limitExceededLatency(),
                result.temporarilyUnavailableCount(), result.temporarilyUnavailableLatency(),
                result.otherFailureCount(), result.otherFailureLatency());

        return result;
    }
}
