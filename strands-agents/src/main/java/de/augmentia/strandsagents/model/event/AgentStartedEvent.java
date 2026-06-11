package de.augmentia.strandsagents.model.event;

import java.time.Instant;

public record AgentStartedEvent(
    String sessionId,
    Instant timestamp,
    String initialPrompt
) implements AgentEvent {}
