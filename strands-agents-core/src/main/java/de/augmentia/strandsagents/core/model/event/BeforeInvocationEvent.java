package de.augmentia.strandsagents.core.model.event;

import de.augmentia.strandsagents.core.model.message.Message;
import java.time.Instant;
import java.util.List;

public record BeforeInvocationEvent(
    String sessionId,
    Instant timestamp,
    StringBuilder systemPrompt,
    List<Message> currentMessages
) implements AgentEvent {}
