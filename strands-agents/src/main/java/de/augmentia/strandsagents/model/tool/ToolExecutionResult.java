package de.augmentia.strandsagents.model.tool;

public record ToolExecutionResult(
    String toolCallId,
    String toolName,
    String result,
    boolean isError
) {}
