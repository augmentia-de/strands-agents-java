package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.session.Session;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SessionManager {
    Session createSession(String agentName, Map<String, Object> metadata);
    Optional<Session> loadSession(String sessionId);
    void saveSession(Session session);
    void deleteSession(String sessionId);
    List<Session> listSessions(String agentName);
    List<Session> searchByMetadata(String key, String value);
}
