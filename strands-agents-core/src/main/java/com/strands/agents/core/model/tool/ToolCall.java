package com.strands.agents.core.model.tool;

public record ToolCall(
    String id,
    String toolName,
    String arguments
) {}
