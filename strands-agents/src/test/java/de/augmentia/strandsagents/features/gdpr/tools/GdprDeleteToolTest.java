package de.augmentia.strandsagents.features.gdpr.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.features.tools.ToolResult;
import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class GdprDeleteToolTest {

    private static final Instant NOW = Instant.now();

    @Test
    void deletesExistingSession() throws Exception {
        var deleted = new boolean[]{false};
        var sm = new SessionManager() {
            @Override
            public Session createSession(String agentName, Map<String, Object> metadata) {
                return null;
            }
            @Override
            public Optional<Session> loadSession(String id) {
                return "s1".equals(id)
                    ? Optional.of(new Session("s1", "agent", List.of(),
                        new AgentState("s1", List.of(), Map.of(), AgentStatus.IDLE), Map.of(), NOW, NOW))
                    : Optional.empty();
            }
            @Override
            public void saveSession(Session session) {}
            @Override
            public void deleteSession(String id) { deleted[0] = "s1".equals(id); }
            @Override
            public List<Session> listSessions(String agentName) { return List.of(); }
            @Override
            public List<Session> searchByMetadata(String key, String value) { return List.of(); }
        };

        var tool = new GdprDeleteTool(sm);
        var params = new GdprDeleteTool.Params("s1", false);

        var result = tool.execute("call-1", params, new AtomicBoolean(false), null);

        assertThat(deleted[0]).isTrue();
        assertThat(result.content().getFirst().toString()).contains("DELETED");
    }

    @Test
    void returnsSuccessForNonExistentSession() throws Exception {
        var sm = new SessionManager() {
            @Override
            public Session createSession(String agentName, Map<String, Object> metadata) {
                return null;
            }
            @Override
            public Optional<Session> loadSession(String id) { return Optional.empty(); }
            @Override
            public void saveSession(Session session) {}
            @Override
            public void deleteSession(String id) {}
            @Override
            public List<Session> listSessions(String agentName) { return List.of(); }
            @Override
            public List<Session> searchByMetadata(String key, String value) { return List.of(); }
        };

        var tool = new GdprDeleteTool(sm);
        var params = new GdprDeleteTool.Params("nonexistent", false);

        var result = tool.execute("call-1", params, new AtomicBoolean(false), null);

        assertThat(result.content().getFirst().toString()).contains("nicht gefunden");
    }

    @Test
    void throwsForBlankSessionId() {
        var sm = new SessionManager() {
            @Override
            public Session createSession(String agentName, Map<String, Object> metadata) {
                return null;
            }
            @Override
            public Optional<Session> loadSession(String id) { return Optional.empty(); }
            @Override
            public void saveSession(Session session) {}
            @Override
            public void deleteSession(String id) {}
            @Override
            public List<Session> listSessions(String agentName) { return List.of(); }
            @Override
            public List<Session> searchByMetadata(String key, String value) { return List.of(); }
        };

        var tool = new GdprDeleteTool(sm);
        var params = new GdprDeleteTool.Params("   ", false);

        assertThatThrownBy(() ->
            tool.execute("call-1", params, new AtomicBoolean(false), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("darf nicht leer");
    }

    private static SessionManager emptySessionManager() {
        return new SessionManager() {
            @Override public Session createSession(String n, Map<String,Object> m) { return null; }
            @Override public Optional<Session> loadSession(String id) { return Optional.empty(); }
            @Override public void saveSession(Session s) {}
            @Override public void deleteSession(String id) {}
            @Override public List<Session> listSessions(String n) { return List.of(); }
            @Override public List<Session> searchByMetadata(String k, String v) { return List.of(); }
        };
    }

    @Test
    void nameReturnsCorrectIdentifier() {
        var tool = new GdprDeleteTool(emptySessionManager());
        assertThat(tool.name()).isEqualTo("gdpr_delete");
    }

    @Test
    void descriptionIsGerman() {
        var tool = new GdprDeleteTool(emptySessionManager());
        assertThat(tool.description()).contains("Art. 17");
    }
}
