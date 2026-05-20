package de.augmentia.strandsagents.core.model.event;

import de.augmentia.strandsagents.core.model.message.Message;
import java.time.Instant;
import java.util.List;

public record ModelRequestedEvent(
    String sessionId,
    Instant timestamp,
    List<Message> promptHistory
) implements AgentEvent {}
