package de.augmentia.strandsagents.core.model.event;

import java.time.Instant;

public record AgentFinishedEvent(
    String sessionId,
    Instant timestamp,
    String finalAnswer
) implements AgentEvent {}
