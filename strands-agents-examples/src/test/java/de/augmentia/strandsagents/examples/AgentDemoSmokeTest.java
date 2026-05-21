package de.augmentia.strandsagents.examples;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.hook.HookRegistry;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLHook;
import org.junit.jupiter.api.Test;

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
            .with("de.augmentia.strandsagents.core.tools.CalculatorTool")
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
        var hookRegistry = new HookRegistry();
        hookRegistry.register(new HITLHook(
            (action, context) -> {
                var now = java.time.Instant.now();
                return new de.augmentia.strandsagents.core.plugin.guardrail.ApprovalResult(action, true, "auto-approved", now);
            },
            HITLAuthority.CONFIRM,
            java.util.List.of(),
            HITLHook.Mode.SYNC_BLOCKING
        ));
        var agent = new Agent(
            new MockChatModel("Mock: %s"),
            ToolRegistry.builder().standard().build(),
            new ToolExecutor(),
            new SlidingWindowConversationManager(10),
            null,
            null,
            java.util.List.of(),
            hookRegistry
        );
        agent.setSystemPrompt("You are a test assistant.");
        var result = agent.execute("Hello from hook test");
        assertThat(result.finalAnswer()).isNotNull();
    }
}
