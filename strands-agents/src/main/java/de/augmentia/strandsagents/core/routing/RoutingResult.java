package de.augmentia.strandsagents.core.routing;

public record RoutingResult(
    String topic,
    double confidence,
    String originalPrompt
) {}
