package de.augmentia.strandsagents.model.event;

import de.augmentia.strandsagents.model.message.Message;
import java.time.Instant;
import java.util.List;

public record AfterInvocationEvent(
    String sessionId,
    Instant timestamp,
    String response,
    List<Message> messages
) implements AgentEvent {}
