package com.strands.agents.core.model.tool;

public record ToolExecutionResult(
    String toolCallId,
    String toolName,
    String result,
    boolean isError
) {}
