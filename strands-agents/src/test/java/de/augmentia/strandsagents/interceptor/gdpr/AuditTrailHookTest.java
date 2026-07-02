package de.augmentia.strandsagents.interceptor.gdpr;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.interceptor.gdpr.AuditTrailHook.AuditEntry;
import de.augmentia.strandsagents.interceptor.gdpr.AuditTrailHook.AuditStore;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.agent.ExecutionMetrics;
import de.augmentia.strandsagents.model.agent.StopReason;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuditTrailHookTest {

    private static AuditStore inMemoryStore() {
        return new AuditStore() {
            private final List<AuditEntry> entries = new ArrayList<>();
            @Override
            public void write(AuditEntry entry) { entries.add(entry); }
            @Override
            public List<AuditEntry> findByUserId(String userId) { return List.of(); }
            @Override
            public List<AuditEntry> findBySessionId(String sessionId) { return List.of(); }
            @Override
            public List<AuditEntry> findAll() { return List.copyOf(entries); }
            @Override
            public boolean verifyChain() {
                for (int i = 1; i < entries.size(); i++) {
                    if (!entries.get(i).hashPrevious().equals(entries.get(i - 1).hashPayload())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    @Test
    void afterToolCallWritesAuditEntry() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);
        var ctx = new HookContexts.AfterToolCallContext("session-1", "test_tool", "ok", false);

        var result = hook.afterToolCall(ctx, "ok");

        assertThat(result).isInstanceOf(HookResult.Continue.class);
        var entries = store.findAll();
        assertThat(entries).hasSize(1);
        var entry = entries.getFirst();
        assertThat(entry.sessionId()).isEqualTo("session-1");
        assertThat(entry.toolName()).isEqualTo("test_tool");
        assertThat(entry.action()).isEqualTo("tool_call");
        assertThat(entry.isError()).isFalse();
        assertThat(entry.id()).isNotNull();
        assertThat(entry.timestamp()).isNotNull();
    }

    @Test
    void afterToolCallLogsError() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);
        var ctx = new HookContexts.AfterToolCallContext("s1", "fail_tool", "error", true);

        hook.afterToolCall(ctx, "error");

        var entry = store.findAll().getFirst();
        assertThat(entry.isError()).isTrue();
        assertThat(entry.toolName()).isEqualTo("fail_tool");
    }

    @Test
    void afterAgentWritesAuditEntry() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);
        var result = new AgentResult("s1", "final answer", List.of(),
            new ExecutionMetrics(100, 10, 20, 0), StopReason.COMPLETED, null);
        var ctx = new HookContexts.AfterAgentContext("session-1", result);

        var result2 = hook.afterAgent(ctx, "final answer");

        assertThat(result2).isInstanceOf(HookResult.Continue.class);
        var entry = store.findAll().getFirst();
        assertThat(entry.action()).isEqualTo("agent_response");
        assertThat(entry.sessionId()).isEqualTo("session-1");
    }

    @Test
    void maintainsSha256Chain() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);

        hook.afterToolCall(
            new HookContexts.AfterToolCallContext("s1", "tool_a", "ok", false), "ok");
        hook.afterToolCall(
            new HookContexts.AfterToolCallContext("s1", "tool_b", "ok", false), "ok");

        var entries = store.findAll();
        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).hashPrevious()).isEqualTo(entries.get(0).hashPayload());
    }

    @Test
    void firstEntryHasEmptyPreviousHash() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);

        hook.afterToolCall(
            new HookContexts.AfterToolCallContext("s1", "first_tool", "ok", false), "ok");

        assertThat(store.findAll().getFirst().hashPrevious()).isEmpty();
    }

    @Test
    void chainVerificationSucceedsForValidChain() {
        var store = inMemoryStore();
        var hook = new AuditTrailHook(store);

        hook.afterToolCall(
            new HookContexts.AfterToolCallContext("s1", "t1", "ok", false), "ok");
        hook.afterAgent(new HookContexts.AfterAgentContext("s1",
            new AgentResult("s1", "r", List.of(),
                new ExecutionMetrics(0, 0, 0, 0), StopReason.COMPLETED, null)), "r");

        assertThat(store.verifyChain()).isTrue();
    }

    @Test
    void extractUserIdFromSessionId() {
        assertThat(AuditTrailHook.extractUserId("user_42::session_abc")).isEqualTo("user_42");
    }

    @Test
    void extractUserIdReturnsSessionIdIfNoSeparator() {
        assertThat(AuditTrailHook.extractUserId("session-only")).isEqualTo("session-only");
    }

    @Test
    void extractUserIdReturnsUnknownForNull() {
        assertThat(AuditTrailHook.extractUserId(null)).isEqualTo("unknown");
    }
}
