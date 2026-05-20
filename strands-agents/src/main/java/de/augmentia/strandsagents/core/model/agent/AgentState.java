package de.augmentia.strandsagents.core.model.agent;

import de.augmentia.strandsagents.core.model.message.Message;
import java.util.List;
import java.util.Map;

public record AgentState(
    String sessionId,
    List<Message> history,
    Map<String, Object> contextVariables,
    AgentStatus status
) {}
