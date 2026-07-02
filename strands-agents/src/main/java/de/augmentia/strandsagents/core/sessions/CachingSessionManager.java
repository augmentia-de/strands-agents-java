package de.augmentia.strandsagents.core.sessions;

import de.augmentia.strandsagents.model.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingSessionManager implements SessionManager {
    private static final Logger log = LoggerFactory.getLogger(CachingSessionManager.class);
    private final InMemorySessionManager cache;
    private final SessionManager persistent;
    private final Executor executor;
    private final int maxSize;

    public CachingSessionManager(SessionManager persistent) {
        this(persistent, Runnable::run, 1000);
    }

    public CachingSessionManager(SessionManager persistent, Executor executor) {
        this(persistent, executor, 1000);
    }

    public CachingSessionManager(SessionManager persistent, Executor executor, int maxSize) {
        this.cache = new InMemorySessionManager(maxSize);
        this.persistent = persistent;
        this.executor = executor;
        this.maxSize = maxSize;
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

    public void clearCache() {
        cache.clear();
    }

    public int cacheSize() {
        return cache.size();
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
