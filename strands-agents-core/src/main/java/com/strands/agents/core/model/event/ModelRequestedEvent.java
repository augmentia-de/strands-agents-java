package com.strands.agents.core.model.event;

import com.strands.agents.core.model.message.Message;
import java.time.Instant;
import java.util.List;

public record ModelRequestedEvent(
    String sessionId,
    Instant timestamp,
    List<Message> promptHistory
) implements AgentEvent {}
