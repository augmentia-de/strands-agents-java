package de.augmentia.strandsagents.model.event;

import de.augmentia.strandsagents.model.message.Message;
import java.time.Instant;
import java.util.List;

public record BeforeInvocationEvent(
    String sessionId,
    Instant timestamp,
    StringBuilder systemPrompt,
    List<Message> currentMessages
) implements AgentEvent {}
