package com.strands.agents.core.model.event;

import com.strands.agents.core.model.message.Message;
import java.time.Instant;
import java.util.List;

public record AfterInvocationEvent(
    String sessionId,
    Instant timestamp,
    String response,
    List<Message> messages
) implements AgentEvent {}
