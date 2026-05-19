package de.augmentia.strandsagents.core.model.tool;

public record ToolCall(
    String id,
    String toolName,
    String arguments
) {}
