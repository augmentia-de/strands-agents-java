package de.augmentia.strandsagents.interceptor.gdpr.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.model.agent.AgentState;
import de.augmentia.strandsagents.model.agent.AgentStatus;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.model.session.Session;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import de.augmentia.strandsagents.tools.feature.GdprExportTool;
import org.junit.jupiter.api.Test;

class GdprExportToolTest {

    private static final Instant NOW = Instant.now();

    private SessionManager sessionWithMessages(String sessionId, String... messages) {
        var msgs = new ArrayList<de.augmentia.strandsagents.model.message.Message>();
        for (int i = 0; i < messages.length; i++) {
            msgs.add(new UserMessage(String.valueOf(i), NOW, messages[i], Map.of()));
        }
        var session = new Session(sessionId, "test-agent", msgs,
            new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE), Map.of("userId", "user-1"), NOW, NOW);
        return new SessionManager() {
            @Override
            public Session createSession(String agentName, Map<String, Object> metadata) {
                return null;
            }
            @Override
            public Optional<Session> loadSession(String id) {
                return sessionId.equals(id) ? Optional.of(session) : Optional.empty();
            }
            @Override
            public void saveSession(Session session) {}
            @Override
            public void deleteSession(String id) {}
            @Override
            public List<Session> listSessions(String agentName) { return List.of(); }
            @Override
            public List<Session> searchByMetadata(String key, String value) { return List.of(); }
        };
    }

    @Test
    void exportsSessionAsJson() throws Exception {
        var sm = sessionWithMessages("s1", "Hallo", "Wie geht's?");
        var tool = new GdprExportTool(sm);
        var params = new GdprExportTool.Params("s1");

        var result = tool.execute("call-1", params, new AtomicBoolean(false), null);

        assertThat(result.content()).isNotEmpty();
        var text = result.content().getFirst().toString();
        assertThat(text).contains("GDPR_DATA_EXPORT");
        assertThat(text).contains("s1");
        assertThat(text).contains("test-agent");
        assertThat(text).contains("Hallo");
        assertThat(text).contains("Wie geht's?");
    }

    @Test
    void throwsForNonExistentSession() {
        var sm = sessionWithMessages("s1");
        var tool = new GdprExportTool(sm);
        var params = new GdprExportTool.Params("nonexistent");

        assertThatThrownBy(() ->
            tool.execute("call-1", params, new AtomicBoolean(false), null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("nicht gefunden");
    }

    @Test
    void nameReturnsCorrectIdentifier() {
        var sm = sessionWithMessages("s1");
        var tool = new GdprExportTool(sm);
        assertThat(tool.name()).isEqualTo("gdpr_export");
    }

    @Test
    void descriptionIsGerman() {
        var sm = sessionWithMessages("s1");
        var tool = new GdprExportTool(sm);
        assertThat(tool.description()).contains("Art. 20");
    }

    @Test
    void parameterSchemaContainsSessionId() {
        var sm = sessionWithMessages("s1");
        var tool = new GdprExportTool(sm);
        var schema = tool.parameterSchema();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("sessionId")).isNotNull();
        assertThat(schema.get("required")).isNotNull();
    }
}
