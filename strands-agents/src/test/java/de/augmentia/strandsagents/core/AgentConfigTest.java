package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    @Test
    void shouldBuildWithDefaults() {
        var config = AgentConfig.builder().build();

        assertThat(config.name()).isEqualTo("unnamed");
        assertThat(config.maxIterations()).isEqualTo(10);
        assertThat(config.toolRegistry()).isNotNull();
        assertThat(config.systemPrompt()).isEqualTo("");
        assertThat(config.plugins()).isEmpty();
    }

    @Test
    void shouldBuildWithCustomValues() {
        var tools = ToolRegistry.builder()
            .standard()
            .include("bash", "read")
            .build();
        var config = AgentConfig.builder()
            .name("recherche-agent")
            .modelName("openai/gpt-4o")
            .toolRegistry(tools)
            .systemPrompt("Du bist ein Recherche-Agent")
            .maxIterations(15)
            .build();

        assertThat(config.name()).isEqualTo("recherche-agent");
        assertThat(config.modelName()).isEqualTo("openai/gpt-4o");
        assertThat(config.toolRegistry().getToolNames()).contains("bash", "read");
        assertThat(config.systemPrompt()).isEqualTo("Du bist ein Recherche-Agent");
        assertThat(config.maxIterations()).isEqualTo(15);
    }

    @Test
    void shouldBuildWithConversationManager() {
        var slidingWindow = new SlidingWindowConversationManager(5);
        var config = AgentConfig.builder()
            .name("agent-mit-gedaechtnis")
            .conversationManager(slidingWindow)
            .build();

        assertThat(config.conversationManager()).isSameAs(slidingWindow);
        assertThat(config.conversationManager()).isInstanceOf(SlidingWindowConversationManager.class);
    }
}
