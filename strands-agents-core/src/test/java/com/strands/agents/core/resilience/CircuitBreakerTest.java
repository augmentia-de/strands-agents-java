package com.strands.agents.core.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private final CircuitBreakerConfig config = new CircuitBreakerConfig(0.5f, 10, 1);
    private final CircuitBreaker cb = new CircuitBreaker(config);

    @Test
    void shouldStartClosed() {
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldReturnResultWhenClosed() throws Exception {
        var result = cb.call(() -> "ok", () -> "fallback");
        assertThat(result).isEqualTo("ok");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldOpenOnHighFailureRate() {
        for (int i = 0; i < 4; i++) {
            try {
                cb.call(() -> { throw new RuntimeException("fail"); }, () -> "fb");
            } catch (Exception ignored) {
            }
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldReturnFallbackWhenOpen() throws Exception {
        for (int i = 0; i < 4; i++) {
            try {
                cb.call(() -> { throw new RuntimeException("fail"); }, () -> "fb");
            } catch (Exception ignored) {
            }
        }

        var result = cb.call(() -> "should not reach", () -> "fallback");
        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void shouldTransitionToHalfOpenAfterCooldown() throws Exception {
        for (int i = 0; i < 4; i++) {
            try {
                cb.call(() -> { throw new RuntimeException("fail"); }, () -> "fb");
            } catch (Exception ignored) {
            }
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(1100);

        var result = cb.call(() -> "success", () -> "fb");
        assertThat(result).isEqualTo("success");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldReopenOnHalfOpenFailure() throws Exception {
        for (int i = 0; i < 4; i++) {
            try {
                cb.call(() -> { throw new RuntimeException("fail"); }, () -> "fb");
            } catch (Exception ignored) {
            }
        }

        Thread.sleep(1100);

        assertThatThrownBy(() ->
            cb.call(() -> { throw new RuntimeException("still failing"); }, () -> "fb")
        ).isInstanceOf(RuntimeException.class);

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void shouldReset() {
        for (int i = 0; i < 4; i++) {
            try {
                cb.call(() -> { throw new RuntimeException("fail"); }, () -> "fb");
            } catch (Exception ignored) {
            }
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        cb.reset();
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
