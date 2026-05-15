package com.strands.agents.core.model.event;

import com.strands.agents.core.model.tool.ToolExecutionResult;
import java.time.Instant;

public record ToolExecutionFinishedEvent(
    String sessionId,
    Instant timestamp,
    ToolExecutionResult result
) implements AgentEvent {}
