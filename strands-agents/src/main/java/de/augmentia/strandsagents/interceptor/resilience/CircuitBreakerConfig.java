package de.augmentia.strandsagents.interceptor.resilience;

/**
 * Configuration for circuit breaker pattern: failure threshold, sliding window, and half-open delay.
 */
public record CircuitBreakerConfig(
    float failureRateThreshold,
    long slidingWindowSeconds,
    long halfOpenDelaySeconds
) {
    public static final CircuitBreakerConfig DEFAULT = new CircuitBreakerConfig(0.5f, 10, 30);
}
