package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.ToolResult;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentConfigTest {

    private static AgentTool<?> testTool(String name) {
        return new AgentTool<Object>() {
            @Override public String name()  { return name; }
            @Override public String description() { return name; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return JsonNodeFactory.instance.objectNode();
            }
            @Override public ToolResult execute(String id, Object p, AtomicBoolean a, java.util.function.Consumer<ToolResult> u) {
                return ToolResult.success("ok");
            }
        };
    }

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
        var tools = new ToolRegistry();
        tools.register(testTool("bash"));
        tools.register(testTool("read"));
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
