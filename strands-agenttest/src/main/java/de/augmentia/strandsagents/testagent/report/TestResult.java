package de.augmentia.strandsagents.testagent.report;

public record TestResult(
    int variant,
    String label,
    boolean passed,
    long durationMs,
    String stopReason,
    int toolCalls,
    int inputTokens,
    int outputTokens,
    String error
) {}
