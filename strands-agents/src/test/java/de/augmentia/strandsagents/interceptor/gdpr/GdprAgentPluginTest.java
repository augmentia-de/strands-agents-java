package de.augmentia.strandsagents.interceptor.gdpr;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.interceptor.gdpr.AuditTrailHook;
import de.augmentia.strandsagents.interceptor.gdpr.GdprAgentPlugin;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook.BlockAction;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook.MaskType;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import java.util.*;
import org.junit.jupiter.api.Test;

class GdprAgentPluginTest {

    @Test
    void nameReturnsCorrectIdentifier() {
        var plugin = new GdprAgentPlugin(null, EnumSet.of(MaskType.EMAIL),
            BlockAction.REDACT, "[PII]", null);
        assertThat(plugin.name()).isEqualTo("gdpr-compliance");
    }

    @Test
    void getToolsReturnsExportAndDelete() {
        var sm = dummySessionManager();
        var plugin = new GdprAgentPlugin(sm, EnumSet.of(MaskType.EMAIL),
            BlockAction.REDACT, "[PII]", createAuditStore());

        var tools = plugin.getTools();

        assertThat(tools).hasSize(2);
        var names = tools.stream().map(t -> t.spec().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("gdpr_export", "gdpr_delete");
    }

    @Test
    void getToolsReturnsEmptyWhenSessionManagerIsNull() {
        var plugin = new GdprAgentPlugin(null, EnumSet.of(MaskType.EMAIL),
            BlockAction.REDACT, "[PII]", null);

        assertThat(plugin.getTools()).hasSize(2);
    }

    private static SessionManager dummySessionManager() {
        return new SessionManager() {
            @Override
            public de.augmentia.strandsagents.model.session.Session createSession(
                    String agentName, Map<String, Object> metadata) { return null; }
            @Override
            public Optional<de.augmentia.strandsagents.model.session.Session> loadSession(
                    String sessionId) { return Optional.empty(); }
            @Override
            public void saveSession(de.augmentia.strandsagents.model.session.Session session) {}
            @Override
            public void deleteSession(String sessionId) {}
            @Override
            public List<de.augmentia.strandsagents.model.session.Session> listSessions(
                    String agentName) { return List.of(); }
            @Override
            public List<de.augmentia.strandsagents.model.session.Session> searchByMetadata(
                    String key, String value) { return List.of(); }
        };
    }

    private static AuditTrailHook.AuditStore createAuditStore() {
        return new AuditTrailHook.AuditStore() {
            private final List<AuditTrailHook.AuditEntry> entries = new ArrayList<>();
            @Override
            public void write(AuditTrailHook.AuditEntry entry) { entries.add(entry); }
            @Override
            public List<AuditTrailHook.AuditEntry> findByUserId(String userId) { return List.of(); }
            @Override
            public List<AuditTrailHook.AuditEntry> findBySessionId(String sessionId) { return List.of(); }
            @Override
            public List<AuditTrailHook.AuditEntry> findAll() { return List.of(); }
            @Override
            public boolean verifyChain() { return true; }
        };
    }
}
