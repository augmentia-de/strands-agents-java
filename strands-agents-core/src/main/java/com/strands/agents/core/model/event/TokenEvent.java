package com.strands.agents.core.model.event;

import java.time.Instant;

public record TokenEvent(
    String sessionId,
    Instant timestamp,
    String token
) implements AgentEvent {}
