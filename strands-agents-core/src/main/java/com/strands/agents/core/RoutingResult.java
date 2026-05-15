package com.strands.agents.core;

public record RoutingResult(
    String topic,
    double confidence,
    String originalPrompt
) {}
