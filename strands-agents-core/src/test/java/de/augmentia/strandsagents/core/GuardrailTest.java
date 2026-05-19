package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.model.agent.StopReason;

import java.util.List;

import de.augmentia.strandsagents.core.plugin.guardrail.*;
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
        assertThat(plugin.inputGuardrails()).hasSize(1);
        assertThat(plugin.outputGuardrails()).hasSize(1);
        assertThat(plugin.blockAction()).isEqualTo(BlockAction.FALLBACK);
        assertThat(plugin.fallbackMessage()).isEqualTo("blocked");
    }

    @Test
    void guardrailPluginDefaultBlockAction() {
        var plugin = new GuardrailPlugin(List.of(), List.of());
        assertThat(plugin.blockAction()).isEqualTo(BlockAction.FALLBACK);
    }

    @Test
    void guardrailBlockActionThrow() {
        var blockingGuard = (Guardrail) (msgs, ctx) -> GuardrailResult.block("not allowed");
        var plugin = new GuardrailPlugin(List.of(blockingGuard), List.of(), BlockAction.THROW, "");

        var model = new MockChatModel();
        var agent = new StrandsAgent(model, new ToolRegistry(), new ToolExecutor(), null, null, null,
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
        var agent = new StrandsAgent(model, new ToolRegistry(), new ToolExecutor(), null, null, null,
            List.of(plugin));

        var result = agent.execute("test");

        assertThat(result.finalAnswer()).isEqualTo("Anfrage abgelehnt.");
        assertThat(result.stopReason()).isEqualTo(StopReason.ERROR);
    }
}
