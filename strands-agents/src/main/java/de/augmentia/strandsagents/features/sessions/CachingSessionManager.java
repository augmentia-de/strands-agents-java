package de.augmentia.strandsagents.features.sessions;

import de.augmentia.strandsagents.model.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Write-Through-Cache-Decorator: {@link InMemorySessionManager} als Cache
 * vor einem persistenten {@link SessionManager} (z.B. File oder JDBC).
 * <p>
 * Reads aus dem Memory (O(1), kein I/O), Writes synchron (oder asynchron)
 * an beide.
 */
public class CachingSessionManager implements SessionManager {

    private final InMemorySessionManager cache;
    private final SessionManager persistent;
    private final Executor executor;

    public CachingSessionManager(SessionManager persistent) {
        this(persistent, Runnable::run);
    }

    public CachingSessionManager(SessionManager persistent, Executor executor) {
        this.cache = new InMemorySessionManager();
        this.persistent = persistent;
        this.executor = executor;
    }

    @Override
    public Session createSession(String agentName, Map<String, Object> metadata) {
        var session = persistent.createSession(agentName, metadata);
        cache.saveSession(session);
        return session;
    }

    @Override
    public Optional<Session> loadSession(String sessionId) {
        var cached = cache.loadSession(sessionId);
        if (cached.isPresent()) {
            return cached;
        }
        var loaded = persistent.loadSession(sessionId);
        loaded.ifPresent(cache::saveSession);
        return loaded;
    }

    @Override
    public void saveSession(Session session) {
        cache.saveSession(session);
        CompletableFuture.runAsync(() -> persistent.saveSession(session), executor);
    }

    @Override
    public void deleteSession(String sessionId) {
        cache.deleteSession(sessionId);
        persistent.deleteSession(sessionId);
    }

    @Override
    public List<Session> listSessions(String agentName) {
        return persistent.listSessions(agentName);
    }

    @Override
    public List<Session> searchByMetadata(String key, String value) {
        return persistent.searchByMetadata(key, value);
    }
}
