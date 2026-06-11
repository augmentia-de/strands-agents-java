package de.augmentia.strandsagents.model.event;

import de.augmentia.strandsagents.model.message.Message;
import java.time.Instant;
import java.util.List;

public record ModelRequestedEvent(
    String sessionId,
    Instant timestamp,
    List<Message> promptHistory
) implements AgentEvent {}
