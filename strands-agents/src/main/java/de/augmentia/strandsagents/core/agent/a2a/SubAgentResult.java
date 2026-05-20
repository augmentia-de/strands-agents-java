package de.augmentia.strandsagents.core.agent.a2a;

import java.util.Map;

public record SubAgentResult(
    String agentName,
    String prompt,
    String result,
    long durationMs,
    Map<String, String> metadata
) {}
