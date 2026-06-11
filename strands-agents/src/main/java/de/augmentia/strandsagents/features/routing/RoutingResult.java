package de.augmentia.strandsagents.features.routing;

public record RoutingResult(
    String topic,
    double confidence,
    String originalPrompt
) {}
