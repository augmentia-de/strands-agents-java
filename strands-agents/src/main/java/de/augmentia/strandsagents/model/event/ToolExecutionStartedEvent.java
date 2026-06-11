package de.augmentia.strandsagents.model.event;

import de.augmentia.strandsagents.model.tool.ToolCall;
import java.time.Instant;

public record ToolExecutionStartedEvent(
    String sessionId,
    Instant timestamp,
    ToolCall toolCall
) implements AgentEvent {}
