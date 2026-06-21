package de.augmentia.strandsagents.features.sessions;

import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-Memory-Implementierung von {@link SessionManager}.
 * <p>
 * Thread-safe, keine Datei-/DB-Abhängigkeit. Ideal zum Testen, für Demos
 * und um Sessions zwischen mehreren Agenten im selben Prozess zu teilen.
 */
public class InMemorySessionManager implements SessionManager {

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session createSession(String agentName, Map<String, Object> metadata) {
        var now = Instant.now();
        var sessionId = UUID.randomUUID().toString();
        var state = new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE);
        var session = new Session(sessionId, agentName, List.of(), state,
            metadata != null ? Map.copyOf(metadata) : Map.of(), now, now);
        sessions.put(sessionId, session);
        return session;
    }

    @Override
    public Optional<Session> loadSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void saveSession(Session session) {
        sessions.put(session.sessionId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Override
    public List<Session> listSessions(String agentName) {
        return sessions.values().stream()
            .filter(s -> agentName == null || agentName.equals(s.agentName()))
            .map(s -> s)
            .toList();
    }

    @Override
    public List<Session> searchByMetadata(String key, String value) {
        return sessions.values().stream()
            .filter(s -> {
                var v = s.metadata().get(key);
                return v != null && v.toString().equals(value);
            })
            .toList();
    }
}
