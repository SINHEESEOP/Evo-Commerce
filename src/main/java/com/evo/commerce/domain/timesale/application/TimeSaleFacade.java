package com.evo.commerce.domain.timesale.application;

import com.evo.commerce.domain.order.domain.OutboxEvent;
import com.evo.commerce.domain.order.domain.OutboxEventRepository;
import com.evo.commerce.domain.order.domain.OutboxEventStatus;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleMapper;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRequestedEvent;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventCreateRequest;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;
import com.evo.commerce.domain.timesale.dto.TimeSaleParticipationResponse;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSaleFacade {

    private static final String REMAINING_KEY_PREFIX = "time-sale:remaining:";
    private static final String PARTICIPANTS_KEY_PREFIX = "time-sale:participants:";
    private static final String PARTICIPATION_REQUESTED_EVENT_TYPE = "TIME_SALE_PARTICIPATION_REQUESTED";

    private static final long ALREADY_PARTICIPATED_CODE = -1L;
    private static final long SOLD_OUT_CODE = -2L;

    /**
     * KEYS[1] = 잔여 수량 키, KEYS[2] = 참여자 집합 키, ARGV[1] = 참여자 userId, ARGV[2] = 참여 정원.
     * 중복 참여 확인과 잔여 수량 확인, 차감을 하나의 Lua 스크립트 안에서 처리해 원자성을 보장한다 —
     * Redis는 스크립트 실행 도중 다른 명령을 끼워 넣지 않으므로, 두 스레드가 동시에 이 스크립트를
     * 실행해도 항상 한쪽이 완전히 끝난 뒤 다른 쪽이 시작된다.
     */
    private static final String PARTICIPATION_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[1], ARGV[2])
            end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return -1
            end
            local remaining = tonumber(redis.call('GET', KEYS[1]))
            if remaining <= 0 then
                return -2
            end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return remaining - 1
            """;

    /**
     * KEYS[1] = 잔여 수량 키, KEYS[2] = 참여자 집합 키, ARGV[1] = 참여자 userId.
     * reserveParticipation()이 Redis에 이미 커밋한 차감을 되돌리는 보상 스크립트.
     * 예약 확정 자체는 Redis 안에서 끝나므로, 그 뒤에 이어지는 아웃박스 기록이 실패했을 때
     * Redis 쪽 상태를 원래대로 되돌릴 책임은 이 스크립트 말고는 아무도 지지 않는다.
     */
    private static final String RELEASE_SCRIPT = """
            redis.call('SREM', KEYS[2], ARGV[1])
            redis.call('INCR', KEYS[1])
            return 1
            """;

    private final TimeSaleEventRepository timeSaleEventRepository;
    private final ProductRepository productRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

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
     * 잔여 수량 확인, 중복 참여 확인, 차감을 Redis Lua 스크립트로 원자적으로 처리하므로
     * 더 이상 Redisson 분산 락이 필요 없다. 실제 Order/TimeSaleParticipation 생성은
     * 요청 스레드에서 곧바로 하지 않고, 아웃박스에 참여 사실만 기록한 뒤 비동기로 처리한다.
     */
    public TimeSaleParticipationResponse participate(Long userId, Long eventId) {
        TimeSaleEvent event = findEventOrThrow(eventId);
        event.validateInProgress(LocalDateTime.now());

        long result = reserveParticipation(event, userId);

        if (result == ALREADY_PARTICIPATED_CODE) {
            throw new BusinessException(TimeSaleErrorCode.ALREADY_PARTICIPATED);
        }
        if (result == SOLD_OUT_CODE) {
            throw new BusinessException(TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED);
        }

        try {
            publishParticipationRequested(eventId, userId);
        } catch (RuntimeException e) {
            releaseParticipation(event, userId);
            log.error("참여 요청을 아웃박스에 기록하지 못해 Redis 예약을 되돌렸습니다. eventId={}, userId={}", eventId, userId, e);
            throw new BusinessException(TimeSaleErrorCode.PARTICIPATION_TEMPORARILY_UNAVAILABLE);
        }

        return new TimeSaleParticipationResponse(null, null, eventId);
    }

    private long reserveParticipation(TimeSaleEvent event, Long userId) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        return script.eval(
                RScript.Mode.READ_WRITE,
                PARTICIPATION_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(remainingKey(event.getId()), participantsKey(event.getId())),
                userId.toString(),
                String.valueOf(event.getParticipantLimit()));
    }

    private void releaseParticipation(TimeSaleEvent event, Long userId) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        script.eval(
                RScript.Mode.READ_WRITE,
                RELEASE_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(remainingKey(event.getId()), participantsKey(event.getId())),
                userId.toString());
    }

    private void publishParticipationRequested(Long eventId, Long userId) {
        TimeSaleParticipationRequestedEvent event = new TimeSaleParticipationRequestedEvent(eventId, userId);
        outboxEventRepository.save(OutboxEvent.builder()
                .eventType(PARTICIPATION_REQUESTED_EVENT_TYPE)
                .payload(toPayload(event))
                .status(OutboxEventStatus.PENDING)
                .build());
    }

    private String toPayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화에 실패했습니다.", e);
        }
    }

    private String remainingKey(Long eventId) {
        return REMAINING_KEY_PREFIX + eventId;
    }

    private String participantsKey(Long eventId) {
        return PARTICIPANTS_KEY_PREFIX + eventId;
    }

    private TimeSaleEvent findEventOrThrow(Long eventId) {
        return timeSaleEventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(TimeSaleErrorCode.TIME_SALE_EVENT_NOT_FOUND));
    }
}
