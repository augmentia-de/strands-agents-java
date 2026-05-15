package com.strands.agents.core.model.event;

import java.time.Instant;

public sealed interface AgentEvent
    permits AgentStartedEvent, ModelRequestedEvent,
            ToolExecutionStartedEvent, ToolExecutionFinishedEvent,
            AgentFinishedEvent {

    String sessionId();

    Instant timestamp();
}
