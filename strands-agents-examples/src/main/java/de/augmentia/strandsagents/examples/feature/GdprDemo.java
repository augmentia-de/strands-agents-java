package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.facade.GDPRAgentBuilder;
import de.augmentia.strandsagents.interceptor.gdpr.*;
import de.augmentia.strandsagents.interceptor.gdpr.GdprAgentPlugin;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook.BlockAction;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook.MaskType;
import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.model.message.UserMessage;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import de.augmentia.strandsagents.tools.feature.GdprDeleteTool;
import de.augmentia.strandsagents.tools.feature.GdprExportTool;

import java.time.Instant;
import java.util.*;

/**
 * Demonstrates all GDPR compliance integration variants.
 * <p>
 * No API key required -- uses MockChatModel and in-memory data.
 */
public class GdprDemo {

    private static final Instant NOW = Instant.now();
    private static final Map<String, Object> EMPTY_META = Map.of();

    public static void main(String[] args) {
        System.out.println("=".repeat(72));
        System.out.println("  DSGVO/GDPR Compliance Demo");
        System.out.println("=".repeat(72));
        System.out.println();

        demoPiiAnonymizer();
        demoAuditTrail();
        demoGdprBuilder();
        demoExportTool();
        demoDeleteTool();
        demoBlockActionThrow();

        System.out.println("All demos completed successfully.");
    }

    static void demoPiiAnonymizer() {
        System.out.println("--- Variante 1: PII-Anonymizer (Maskierung) ---");

        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL, MaskType.PHONE_NUMBER, MaskType.NAME_DE, MaskType.ADDRESS),
            BlockAction.REDACT,
            "[PII]");

        var messages = List.<de.augmentia.strandsagents.model.message.Message>of(
            new UserMessage("1", NOW, "Kontakt: hans@example.com oder +49 170 1234567", EMPTY_META),
            new UserMessage("2", NOW, "Ich bin Herr Max Mustermann, Musterstr. 42, 12345 Berlin", EMPTY_META));

        var ctx = new HookContexts.BeforeModelCallContext(
            "s1", new StringBuilder(), new ArrayList<>(messages), List.of(), new ArrayList<>());

        var result = hook.beforeModelCall(ctx);

        System.out.println("  Before: Kontakt: hans@example.com oder +49 170 1234567");
        System.out.println("  After:  " + ctx.messages().get(0).content());
        System.out.println("  Before: Ich bin Herr Max Mustermann, Musterstr. 42, 12345 Berlin");
        System.out.println("  After:  " + ctx.messages().get(1).content());
        System.out.println("  Result: " + result.getClass().getSimpleName());
        System.out.println();
    }

    static void demoAuditTrail() {
        System.out.println("--- Variante 2: Audit-Trail mit SHA-256-Chain ---");

        var store = new GDPRAgentBuilder.InMemoryAuditStore();
        var hook = new AuditTrailHook(store);

        hook.afterToolCall(new HookContexts.AfterToolCallContext(
            "s1", "test_tool", "{\"result\": \"ok\"}", false), "{\"result\": \"ok\"}");
        hook.afterAgent(new HookContexts.AfterAgentContext(
            "s1", new de.augmentia.strandsagents.model.agent.AgentResult(
                "s1", "Antwort wurde verarbeitet", null, null, null, null)),
            "Antwort wurde verarbeitet");

        var entries = store.findAll();
        System.out.println("  Audit-Einträge: " + entries.size());
        System.out.println("  Chain intakt:   " + store.verifyChain());
        System.out.println();
    }

    static void demoGdprBuilder() {
        System.out.println("--- Variante 3: GDPRAgentBuilder mit Agent-Builder (Audit-Trail im Agenten-Loop) ---");

        var auditStore = new GDPRAgentBuilder.InMemoryAuditStore();
        var sm = createSessionManager();

        var plugin = new GdprAgentPlugin(
            sm,
            EnumSet.of(MaskType.EMAIL, MaskType.PHONE_NUMBER, MaskType.NAME_DE),
            BlockAction.REDACT,
            "[PII]",
            auditStore);

        var hookRegistry = new HookRegistry();

        var agent = Agent.builder()
            .model(new MockChatModel("GDPR response: %s"))
            .plugins(List.of(plugin))
            .hookRegistry(hookRegistry)
            .systemPrompt("You are a GDPR-compliant assistant.")
            .build();

        System.out.println("  Agent execution with PII in prompt:");
        var agentResult = agent.execute(
            "Meine Email ist max@example.com und meine Nummer +49 170 1234567");

        System.out.println("  Model input (vor Maskierung): " + agentResult.finalAnswer());
        System.out.println();
        System.out.println("  Hinweis: PII-Maskierung im AgentLoop erfordert eine");
        System.out.println("  Rückschreibung der domainMessages → chatMemory (Core-Änderung).");
        System.out.println("  Der PiiAnonymizerHook arbeitet korrekt (siehe Variante 1),");
        System.out.println("  die Audit-Hooks feuern zuverlässig:");

        var auditEntries = auditStore.findAll();
        System.out.println("  Audit-Trail: " + auditEntries.size() + " Einträge");
        for (var entry : auditEntries) {
            System.out.println("    [" + entry.action() + "] " + (entry.toolName() != null ? entry.toolName() : "-")
                + "  hash=" + entry.hashPayload().substring(0, 12) + "...");
        }
        System.out.println("  Chain intakt: " + auditStore.verifyChain());
        System.out.println();
    }

    static void demoExportTool() {
        System.out.println("--- Variante 4: GDPR Export (Art. 20 DSGVO) ---");

        var sm = createSessionManager();
        var tool = new GdprExportTool(sm);
        var params = new GdprExportTool.Params("s1");

        try {
            var result = tool.execute("call-1", params, new java.util.concurrent.atomic.AtomicBoolean(false), null);
            System.out.println("  Export erfolgreich: " + result.content().getFirst().toString().substring(0, 80) + "...");
        } catch (Exception e) {
            System.out.println("  Fehler: " + e.getMessage());
        }
        System.out.println();
    }

    static void demoDeleteTool() {
        System.out.println("--- Variante 5: GDPR Delete (Art. 17 DSGVO) ---");

        var sm = createSessionManager();
        var tool = new GdprDeleteTool(sm);
        var params = new GdprDeleteTool.Params("s1", false);

        try {
            var result = tool.execute("call-2", params, new java.util.concurrent.atomic.AtomicBoolean(false), null);
            System.out.println("  Löschung: " + result.content().getFirst().toString());
        } catch (Exception e) {
            System.out.println("  Fehler: " + e.getMessage());
        }
        System.out.println();
    }

    static void demoBlockActionThrow() {
        System.out.println("--- Variante 6: BlockAction.THROW ---");

        var hook = new PiiAnonymizerHook(
            EnumSet.of(MaskType.EMAIL), BlockAction.THROW, "[PII]");

        var messages = List.<de.augmentia.strandsagents.model.message.Message>of(
            new UserMessage("1", NOW, "email@test.com", EMPTY_META));

        var ctx = new HookContexts.BeforeModelCallContext(
            "s1", new StringBuilder(), new ArrayList<>(messages), List.of(), new ArrayList<>());

        var result = hook.beforeModelCall(ctx);

        if (result instanceof HookResult.Cancel cancel) {
            System.out.println("  Prompt blockiert: " + cancel.reason());
        } else {
            System.out.println("  Prompt durchgelassen (unerwartet)");
        }
        System.out.println();
    }

    private static SessionManager createSessionManager() {
        var session = new de.augmentia.strandsagents.model.session.Session(
            "s1", "test-agent",
            List.of(new UserMessage("1", NOW, "Hallo Welt", EMPTY_META)),
            new de.augmentia.strandsagents.model.agent.AgentState(
                "s1", List.of(), Map.of(),
                de.augmentia.strandsagents.model.agent.AgentStatus.IDLE),
            Map.of("userId", "user-1"), NOW, NOW);

        return new SessionManager() {
            @Override
            public de.augmentia.strandsagents.model.session.Session createSession(
                    String agentName, Map<String, Object> metadata) { return null; }
            @Override
            public Optional<de.augmentia.strandsagents.model.session.Session> loadSession(String id) {
                return "s1".equals(id) ? Optional.of(session) : Optional.empty();
            }
            @Override
            public void saveSession(de.augmentia.strandsagents.model.session.Session s) {}
            @Override
            public void deleteSession(String id) {}
            @Override
            public List<de.augmentia.strandsagents.model.session.Session> listSessions(String n) { return List.of(); }
            @Override
            public List<de.augmentia.strandsagents.model.session.Session> searchByMetadata(String k, String v) { return List.of(); }
        };
    }
}
