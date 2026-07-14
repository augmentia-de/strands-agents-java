package de.augmentia.strandsagents.interceptor.resilience;

/**
 * Configuration for retry behaviour with exponential backoff.
 */
public record RetryConfig(
    int maxAttempts,
    long backoffDelayMs,
    double backoffMultiplier
) {
    public static final RetryConfig DEFAULT = new RetryConfig(3, 1000, 2.0);
}
