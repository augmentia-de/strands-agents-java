package com.strands.agents.core.model.event;

import com.strands.agents.core.model.tool.ToolCall;
import java.time.Instant;

public record ToolExecutionStartedEvent(
    String sessionId,
    Instant timestamp,
    ToolCall toolCall
) implements AgentEvent {}
