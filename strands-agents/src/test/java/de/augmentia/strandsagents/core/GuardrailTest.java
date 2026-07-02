package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.interceptor.guardrails.*;
import de.augmentia.strandsagents.model.agent.StopReason;

import java.util.List;

import org.junit.jupiter.api.Test;

class GuardrailTest {

    @Test
    void guardrailResultFactoryMethods() {
        var ok = GuardrailResult.ok();
        assertThat(ok.pass()).isTrue();

        var blocked = GuardrailResult.block("verboten");
        assertThat(blocked.pass()).isFalse();
        assertThat(blocked.reason()).isEqualTo("verboten");

        var sanitized = GuardrailResult.block("pii found", "***");
        assertThat(sanitized.sanitized()).isEqualTo("***");
    }

    @Test
    void guardrailPluginStoresConfig() {
        var inputGuard = List.of((Guardrail) (msgs, ctx) -> GuardrailResult.ok());
        var outputGuard = List.of((Guardrail) (msgs, ctx) -> GuardrailResult.ok());
        var plugin = new GuardrailPlugin(inputGuard, outputGuard, BlockAction.FALLBACK, "blocked");

        assertThat(plugin.name()).isEqualTo("guardrails");
        assertThat(plugin.getInputGuardrails()).hasSize(1);
        assertThat(plugin.getOutputGuardrails()).hasSize(1);
        assertThat(plugin.getBlockAction()).isEqualTo(BlockAction.FALLBACK);
        assertThat(plugin.getFallbackMessage()).isEqualTo("blocked");
    }

    @Test
    void guardrailPluginDefaultBlockAction() {
        var plugin = new GuardrailPlugin(List.of(), List.of());
        assertThat(plugin.getBlockAction()).isEqualTo(BlockAction.FALLBACK);
    }

    @Test
    void guardrailBlockActionThrow() {
        var blockingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("not allowed");
        var plugin = new GuardrailPlugin(List.of(blockingGuard), List.of(), BlockAction.THROW, "");

        var model = new MockChatModel();
        var agent = new Agent(model, new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            List.of(plugin));

        assertThatThrownBy(() -> agent.execute("test"))
            .isInstanceOf(GuardrailException.class)
            .hasMessageContaining("not allowed");
    }

    @Test
    void guardrailBlockActionFallback() {
        var blockingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("not allowed");
        var plugin = new GuardrailPlugin(List.of(blockingGuard), List.of(), BlockAction.FALLBACK,
            "Anfrage abgelehnt.");

        var model = new MockChatModel();
        var agent = new Agent(model, new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            List.of(plugin));

        var result = agent.execute("test");

        assertThat(result.finalAnswer()).isEqualTo("Anfrage abgelehnt.");
        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }

    @Test
    void guardrailSanitizedOutput_usedInResponse() {
        var sanitizingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("pii found", "***");
        var plugin = new GuardrailPlugin(List.of(), List.of(sanitizingGuard), BlockAction.FALLBACK,
            "fallback");

        var model = new MockChatModel();
        var agent = new Agent(model, new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            List.of(plugin));

        var result = agent.execute("test");

        assertThat(result.finalAnswer()).isEqualTo("***");
        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }

    @Test
    void guardrailSanitizedOutput_usedWithThrowAction() {
        var sanitizingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("pii found", "***");
        var plugin = new GuardrailPlugin(List.of(), List.of(sanitizingGuard), BlockAction.THROW, "");

        var model = new MockChatModel();
        var agent = new Agent(model, new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            List.of(plugin));

        assertThatThrownBy(() -> agent.execute("test"))
            .isInstanceOf(GuardrailException.class)
            .hasMessageContaining("pii found")
            .hasMessageContaining("***");
    }

    @Test
    void guardrailSanitizedNull_usesFallbackMessage() {
        var blockingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("not allowed");
        var plugin = new GuardrailPlugin(List.of(blockingGuard), List.of(), BlockAction.FALLBACK,
            "standard fallback");

        var model = new MockChatModel();
        var agent = new Agent(model, new ToolRegistry(), new DefaultToolExecutor(), null, null, null,
            List.of(plugin));

        var result = agent.execute("test");

        assertThat(result.finalAnswer()).isEqualTo("standard fallback");
    }
}
