package de.augmentia.strandsagents.model.event;

import java.time.Instant;

public record AgentFinishedEvent(
    String sessionId,
    Instant timestamp,
    String finalAnswer
) implements AgentEvent {}
