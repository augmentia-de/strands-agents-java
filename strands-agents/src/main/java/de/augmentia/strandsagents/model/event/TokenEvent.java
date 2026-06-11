package de.augmentia.strandsagents.model.event;

import java.time.Instant;

public record TokenEvent(
    String sessionId,
    Instant timestamp,
    String token
) implements AgentEvent {}
