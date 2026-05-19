package de.augmentia.strandsagents.core.model.event;

import de.augmentia.strandsagents.core.model.tool.ToolExecutionResult;
import java.time.Instant;

public record ToolExecutionFinishedEvent(
    String sessionId,
    Instant timestamp,
    ToolExecutionResult result
) implements AgentEvent {}
