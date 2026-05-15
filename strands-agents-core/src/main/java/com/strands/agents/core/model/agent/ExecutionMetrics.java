package com.strands.agents.core.model.agent;

public record ExecutionMetrics(
    long durationMs,
    int inputTokens,
    int outputTokens,
    int toolCallsCount
) {}
