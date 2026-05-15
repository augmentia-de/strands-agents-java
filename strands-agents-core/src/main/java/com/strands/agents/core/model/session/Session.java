package com.strands.agents.core.model.session;

import com.strands.agents.core.model.agent.AgentState;
import com.strands.agents.core.model.message.Message;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Session(
    String sessionId,
    String agentName,
    List<Message> messages,
    AgentState state,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}
