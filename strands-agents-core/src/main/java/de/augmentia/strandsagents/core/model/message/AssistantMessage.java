package de.augmentia.strandsagents.core.model.message;

import de.augmentia.strandsagents.core.model.tool.ToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AssistantMessage(
    String id,
    Instant timestamp,
    String content,
    Map<String, Object> metadata,
    List<ToolCall> toolCalls
) implements Message {}
