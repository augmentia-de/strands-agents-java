package com.strands.agents.core.model.agent;

import com.strands.agents.core.model.message.Message;
import java.util.List;
import java.util.Map;

public record AgentState(
    String sessionId,
    List<Message> history,
    Map<String, Object> contextVariables,
    AgentStatus status
) {}
