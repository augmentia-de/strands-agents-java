package de.augmentia.strandsagents.facade;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.interceptor.gdpr.AuditTrailHook;
import de.augmentia.strandsagents.interceptor.gdpr.PiiAnonymizerHook;
import org.junit.jupiter.api.Test;

class GDPRAgentBuilderTest {

    @Test
    void buildsAgentWithGdprPlugin() {
        var agent = new GDPRAgentBuilder()
            .maskTypes(PiiAnonymizerHook.MaskType.EMAIL)
            .blockAction(PiiAnonymizerHook.BlockAction.REDACT)
            .replacement("[PII]")
            .auditStore(GDPRAgentBuilder.AuditStoreType.IN_MEMORY)
            .and()
            .withSystemPrompt("GDPR compliant agent")
            .build();

        assertThat(agent).isNotNull();
    }

    @Test
    void andMethodReturnsStrandsAgentBuilder() {
        var builder = new GDPRAgentBuilder()
            .maskTypes(PiiAnonymizerHook.MaskType.EMAIL)
            .blockAction(PiiAnonymizerHook.BlockAction.REDACT)
            .and();

        assertThat(builder).isInstanceOf(StrandsAgentBuilder.class);
    }

    @Test
    void configuresFileAuditStore() {
        var auditDir = "target/test-audit-" + System.currentTimeMillis();
        var builder = new GDPRAgentBuilder()
            .maskTypes(PiiAnonymizerHook.MaskType.EMAIL)
            .blockAction(PiiAnonymizerHook.BlockAction.REDACT)
            .replacement("[PII]")
            .auditStore(GDPRAgentBuilder.AuditStoreType.FILE)
            .auditDir(auditDir);

        var store = new GDPRAgentBuilder.FileAuditStore(java.nio.file.Path.of(auditDir));
        var entry = new AuditTrailHook.AuditEntry(
            "test-1", java.time.Instant.now(), "s1", "u1",
            "tool_call", "test_tool", false, "", "hash");
        store.write(entry);
        assertThat(store.verifyChain()).isTrue();
    }

    @Test
    void inMemoryAuditStoreMaintainsChain() {
        var store = new GDPRAgentBuilder.InMemoryAuditStore();

        store.write(new AuditTrailHook.AuditEntry(
            "1", java.time.Instant.now(), "s1", "u1",
            "tool_call", "t1", false, "", "hash1"));
        store.write(new AuditTrailHook.AuditEntry(
            "2", java.time.Instant.now(), "s1", "u1",
            "tool_call", "t2", false, "hash1", "hash2"));

        assertThat(store.verifyChain()).isTrue();
        assertThat(store.findAll()).hasSize(2);
        assertThat(store.findBySessionId("s1")).hasSize(2);
        assertThat(store.findByUserId("u1")).hasSize(2);
    }

    @Test
    void inMemoryAuditStoreDetectsTamperedChain() {
        var store = new GDPRAgentBuilder.InMemoryAuditStore();

        store.write(new AuditTrailHook.AuditEntry(
            "1", java.time.Instant.now(), "s1", "u1",
            "tool_call", "t1", false, "", "hash1"));
        store.write(new AuditTrailHook.AuditEntry(
            "2", java.time.Instant.now(), "s1", "u1",
            "tool_call", "t2", false, "wrong_prev_hash", "hash2"));

        assertThat(store.verifyChain()).isFalse();
    }
}
