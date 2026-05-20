package de.augmentia.strandsagents.core.agent.routing;

public record RoutingResult(
    String topic,
    double confidence,
    String originalPrompt
) {}
