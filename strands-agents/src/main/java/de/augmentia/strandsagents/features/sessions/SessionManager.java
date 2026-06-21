package de.augmentia.strandsagents.features.sessions;

import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SessionManager {
    Session createSession(String agentName, Map<String, Object> metadata);
    Optional<Session> loadSession(String sessionId);
    void saveSession(Session session);
    void deleteSession(String sessionId);
    List<Session> listSessions(String agentName);
    List<Session> searchByMetadata(String key, String value);

    default Session copySession(String sourceSessionId, String newAgentName, Integer count) {
        var source = loadSession(sourceSessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sourceSessionId));
        var newId = UUID.randomUUID().toString();
        var allMessages = source.messages();
        var selected = count != null
            ? allMessages.subList(0, Math.min(count, allMessages.size()))
            : allMessages;
        var messages = List.copyOf(selected);
        var state = new AgentState(newId, messages, source.state().contextVariables(), AgentStatus.IDLE);
        var now = Instant.now();
        var copy = new Session(newId, newAgentName != null ? newAgentName : source.agentName(),
            messages, state, source.metadata(), now, now);
        saveSession(copy);
        return copy;
    }
}
