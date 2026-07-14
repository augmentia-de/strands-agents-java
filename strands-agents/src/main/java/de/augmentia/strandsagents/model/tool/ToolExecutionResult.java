package de.augmentia.strandsagents.model.tool;

/** Result of a tool execution. */
/**
 * Result of a single tool execution, including the raw output and error status.
 */
public record ToolExecutionResult(
    String toolCallId,
    String toolName,
    String result,
    boolean isError
) {}
