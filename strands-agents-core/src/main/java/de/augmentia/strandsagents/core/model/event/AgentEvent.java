package de.augmentia.strandsagents.core.model.event;

import java.time.Instant;

public sealed interface AgentEvent
    permits AgentStartedEvent, BeforeInvocationEvent, ModelRequestedEvent,
            ToolExecutionStartedEvent, ToolExecutionFinishedEvent,
            AgentFinishedEvent, TokenEvent, AgentStateChangedEvent,
            AfterInvocationEvent {

    String sessionId();

    Instant timestamp();
}
