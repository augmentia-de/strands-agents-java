package com.strands.agents.core.model.event;

import com.strands.agents.core.model.agent.AgentPhase;
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
