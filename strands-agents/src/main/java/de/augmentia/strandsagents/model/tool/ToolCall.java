package de.augmentia.strandsagents.model.tool;

public record ToolCall(
    String id,
    String toolName,
    String arguments
) {}
