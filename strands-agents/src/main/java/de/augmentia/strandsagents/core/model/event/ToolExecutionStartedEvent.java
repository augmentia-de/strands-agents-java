package de.augmentia.strandsagents.core.model.event;

import de.augmentia.strandsagents.core.model.tool.ToolCall;
import java.time.Instant;

public record ToolExecutionStartedEvent(
    String sessionId,
    Instant timestamp,
    ToolCall toolCall
) implements AgentEvent {}
