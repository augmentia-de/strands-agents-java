package com.strands.agents.core.model.event;

import com.strands.agents.core.model.message.Message;
import java.time.Instant;
import java.util.List;

public record BeforeInvocationEvent(
    String sessionId,
    Instant timestamp,
    StringBuilder systemPrompt,
    List<Message> currentMessages
) implements AgentEvent {}
