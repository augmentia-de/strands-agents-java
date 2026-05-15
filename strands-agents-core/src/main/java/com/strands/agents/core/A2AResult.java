package com.strands.agents.core;

import java.util.Map;

public record A2AResult(
    String agentName,
    String prompt,
    String result,
    long durationMs,
    Map<String, String> metadata
) {}
