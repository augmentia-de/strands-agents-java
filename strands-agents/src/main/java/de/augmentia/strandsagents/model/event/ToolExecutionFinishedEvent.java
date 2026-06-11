package de.augmentia.strandsagents.model.event;

import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import java.time.Instant;

public record ToolExecutionFinishedEvent(
    String sessionId,
    Instant timestamp,
    ToolExecutionResult result
) implements AgentEvent {}
