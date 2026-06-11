package de.augmentia.strandsagents.model.message;

import java.time.Instant;
import java.util.Map;

public record ToolMessage(
    String id,
    Instant timestamp,
    String content,
    Map<String, Object> metadata,
    String toolCallId,
    String toolName
) implements Message {}
