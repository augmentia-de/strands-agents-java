package de.augmentia.strandsagents.features.resilience;

public record ResilienceConfig(
    RetryConfig retryConfig,
    CircuitBreakerConfig circuitBreakerConfig
) {
    public static final ResilienceConfig DEFAULT = new ResilienceConfig(RetryConfig.DEFAULT, CircuitBreakerConfig.DEFAULT);
    public static final ResilienceConfig NONE = new ResilienceConfig(null, null);
}
