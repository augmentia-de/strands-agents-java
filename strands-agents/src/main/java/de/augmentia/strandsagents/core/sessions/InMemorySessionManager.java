package de.augmentia.strandsagents.core.sessions;

import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemorySessionManager implements SessionManager {
    private static final Logger log = LoggerFactory.getLogger(InMemorySessionManager.class);
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final int maxSize;
    private final AtomicInteger size = new AtomicInteger(0);

    public InMemorySessionManager() {
        this(Integer.getInteger("session.max.memory.size", 1000));
    }

    public InMemorySessionManager(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public Session createSession(String agentName, Map<String, Object> metadata) {
        var now = Instant.now();
        var sessionId = UUID.randomUUID().toString();
        var state = new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE);
        var session = new Session(sessionId, agentName, List.of(), state,
            metadata != null ? Map.copyOf(metadata) : Map.of(), now, now);
        sessions.put(sessionId, session);
        int currentSize = size.incrementAndGet();
        if (currentSize > maxSize) {
            log.warn("Session cache exceeded max size ({}), triggering cleanup", maxSize);
            cleanupOldest();
        }
        return session;
    }

    private void cleanupOldest() {
        if (sessions.isEmpty()) return;
        String oldestKey = sessions.keySet().stream()
            .min(String::compareTo)
            .orElse(null);
        if (oldestKey != null) {
            sessions.remove(oldestKey);
            size.decrementAndGet();
        }
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
        if (sessions.remove(sessionId) != null) {
            size.decrementAndGet();
        }
    }

    public void clear() {
        sessions.clear();
        size.set(0);
    }

    public int size() {
        return size.get();
    }

    @Override
    public List<Session> listSessions(String agentName) {
        return sessions.values().stream()
            .filter(s -> agentName == null || agentName.equals(s.agentName()))
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
