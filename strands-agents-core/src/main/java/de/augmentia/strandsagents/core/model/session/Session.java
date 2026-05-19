package de.augmentia.strandsagents.core.model.session;

import de.augmentia.strandsagents.core.model.agent.AgentState;
import de.augmentia.strandsagents.core.model.message.Message;
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
