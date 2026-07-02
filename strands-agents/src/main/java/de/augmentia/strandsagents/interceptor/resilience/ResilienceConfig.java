package de.augmentia.strandsagents.interceptor.resilience;

import java.time.Duration;

public record ResilienceConfig(
    RetryConfig retryConfig,
    CircuitBreakerConfig circuitBreakerConfig,
    Duration modelTimeout
) {
    public static final ResilienceConfig DEFAULT = new ResilienceConfig(RetryConfig.DEFAULT, CircuitBreakerConfig.DEFAULT, null);
    public static final ResilienceConfig NONE = new ResilienceConfig(null, null, null);

    public ResilienceConfig(RetryConfig retryConfig, CircuitBreakerConfig circuitBreakerConfig) {
        this(retryConfig, circuitBreakerConfig, null);
    }
}
