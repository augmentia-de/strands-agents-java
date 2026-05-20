package de.augmentia.strandsagents.core.model.event;

import de.augmentia.strandsagents.core.model.agent.AgentPhase;
import java.time.Instant;

public record AgentStateChangedEvent(
    String sessionId,
    Instant timestamp,
    AgentPhase previousPhase,
    AgentPhase currentPhase,
    String goal,
    int iterationCount,
    int revisionCount
) implements AgentEvent {}
