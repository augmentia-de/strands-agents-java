package de.augmentia.strandsagents.examples;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.guardrails.ApprovalResult;
import de.augmentia.strandsagents.features.hitl.HITLAuthority;
import de.augmentia.strandsagents.features.hitl.HITLPlugin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class AgentDemoSmokeTest {

    @Test
    void agentExecutesSuccessfullyWithMockModel() {
        var agent = new Agent(new MockChatModel("Mock-Antwort: %s"));
        agent.setSystemPrompt("You are a test assistant.");
        var result = agent.execute("Hello");
        assertThat(result.finalAnswer()).isNotNull();
        assertThat(result.stopReason()).isNotNull();
    }

    @Test
    void agentWithToolsExecutesSuccessfully() {
        var registry = ToolRegistry.builder()
            .standard()
            .with("de.augmentia.strandsagents.features.tools.CalculatorTool")
            .build();
        var agent = new Agent(
            new MockChatModel(),
            registry,
            new ToolExecutor(),
            new SlidingWindowConversationManager(10),
            null,
            null
        );
        agent.setSystemPrompt("You are a test assistant.");
        var result = agent.execute("What tools do you have?");
        assertThat(result.finalAnswer()).isNotNull();
    }

    @Test
    void agentWithHooksExecutesSuccessfully() {
        var hitlPlugin = new HITLPlugin(
            (action, context) -> new ApprovalResult(action, true, "auto-approved", Instant.now()),
            HITLAuthority.CONFIRM,
            List.of()
        );
        List<Plugin> plugins = List.of(hitlPlugin);
        var agent = new Agent(
            new MockChatModel("Mock: %s"),
            ToolRegistry.builder().standard().build(),
            new ToolExecutor(),
            new SlidingWindowConversationManager(10),
            null,
            null,
            plugins
        );
        agent.setSystemPrompt("You are a test assistant.");
        var result = agent.execute("Hello from hook test");
        assertThat(result.finalAnswer()).isNotNull();
    }
}
