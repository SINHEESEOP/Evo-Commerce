package com.evo.commerce.domain.notification.domain;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry sseEmitterRegistry = new SseEmitterRegistry();

    @Test
    void 구독하면_해당_사용자의_구독_목록에서_찾을_수_있다() {
        SseEmitter emitter = sseEmitterRegistry.register(1L);

        assertThat(sseEmitterRegistry.findByUserId(1L)).containsExactly(emitter);
    }

    @Test
    void 구독한_적_없는_사용자를_조회하면_빈_목록을_반환한다() {
        assertThat(sseEmitterRegistry.findByUserId(999L)).isEmpty();
    }

    @Test
    void 같은_사용자가_여러_번_구독하면_전부_목록에_쌓인다() {
        sseEmitterRegistry.register(1L);
        sseEmitterRegistry.register(1L);

        assertThat(sseEmitterRegistry.findByUserId(1L)).hasSize(2);
    }
}
