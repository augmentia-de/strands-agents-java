package de.augmentia.strandsagents.examples.orchestrator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskResult(
    String taskName,
    String result,
    int toolCalls,
    int errors,
    int timeouts,
    String stopReason,
    long durationMs
) {}
