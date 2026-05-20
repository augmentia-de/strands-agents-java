package de.augmentia.strandsagents.core.model.agent;

public record ExecutionMetrics(
    long durationMs,
    int inputTokens,
    int outputTokens,
    int toolCallsCount
) {}
