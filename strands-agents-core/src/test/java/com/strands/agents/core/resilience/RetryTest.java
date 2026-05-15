package com.strands.agents.core.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryTest {

    @Test
    void shouldSucceedOnFirstAttempt() throws Exception {
        var result = Retry.run(() -> "ok", new RetryConfig(3, 10, 1.0));
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldSucceedAfterRetries() throws Exception {
        var counter = new AtomicInteger();
        var result = Retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient error");
            }
            return "success";
        }, new RetryConfig(5, 10, 1.0));

        assertThat(result).isEqualTo("success");
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void shouldFailAfterMaxAttempts() {
        var counter = new AtomicInteger();
        assertThatThrownBy(() ->
            Retry.run(() -> {
                counter.incrementAndGet();
                throw new RuntimeException("persistent error");
            }, new RetryConfig(3, 10, 1.0))
        ).isInstanceOf(RuntimeException.class)
            .hasMessageContaining("persistent error");

        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryNonRetryableExceptions() {
        var counter = new AtomicInteger();
        assertThatThrownBy(() ->
            Retry.run(() -> {
                counter.incrementAndGet();
                throw new IllegalArgumentException("bad request");
            }, new RetryConfig(3, 10, 1.0))
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void shouldApplyExponentialBackoff() throws Exception {
        var counter = new AtomicInteger();
        var start = System.nanoTime();

        Retry.run(() -> {
            if (counter.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "ok";
        }, new RetryConfig(3, 50, 2.0));

        var elapsed = (System.nanoTime() - start) / 1_000_000;
        assertThat(counter.get()).isEqualTo(3);
        assertThat(elapsed).isGreaterThanOrEqualTo(50);
    }
}
