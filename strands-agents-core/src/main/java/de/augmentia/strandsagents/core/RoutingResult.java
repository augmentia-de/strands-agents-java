package de.augmentia.strandsagents.core;

public record RoutingResult(
    String topic,
    double confidence,
    String originalPrompt
) {}
