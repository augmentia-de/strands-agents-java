package de.augmentia.strandsagents.model.agent;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;
import java.util.Map;

public record AgentState(
    String sessionId,
    List<Message> history,
    Map<String, Object> contextVariables,
    AgentStatus status
) {}
