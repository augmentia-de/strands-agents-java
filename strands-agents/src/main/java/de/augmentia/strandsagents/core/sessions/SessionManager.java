package de.augmentia.strandsagents.core.sessions;

import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages lifecycle of agent sessions: create, load, save, delete, list, and search.
 */
public interface SessionManager {
    /**
     * Creates a new session for the given agent with optional metadata.
     */
    Session createSession(String agentName, Map<String, Object> metadata);

    /**
     * Loads a session by its unique identifier.
     */
    Optional<Session> loadSession(String sessionId);

    /**
     * Persists the given session.
     */
    void saveSession(Session session);

    /**
     * Deletes a session by its unique identifier.
     */
    void deleteSession(String sessionId);

    /**
     * Lists all sessions for the given agent name.
     */
    List<Session> listSessions(String agentName);

    /**
     * Searches sessions by a metadata key-value pair.
     */
    List<Session> searchByMetadata(String key, String value);

    /**
     * Copies a session, optionally limiting messages and rebinding to a new agent name.
     */
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
