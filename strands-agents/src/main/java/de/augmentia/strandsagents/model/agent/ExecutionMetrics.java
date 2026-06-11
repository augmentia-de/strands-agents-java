package de.augmentia.strandsagents.model.agent;

public record ExecutionMetrics(
    long durationMs,
    int inputTokens,
    int outputTokens,
    int toolCallsCount
) {}
