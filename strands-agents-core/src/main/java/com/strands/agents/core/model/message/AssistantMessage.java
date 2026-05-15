package com.strands.agents.core.model.message;

import com.strands.agents.core.model.tool.ToolCall;
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
