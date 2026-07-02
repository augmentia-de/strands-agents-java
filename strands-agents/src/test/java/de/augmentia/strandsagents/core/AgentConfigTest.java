package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.AgentSettings;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
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
        var settings = AgentSettings.builder().build();
        var config = AgentConfig.builder().build();

        assertThat(settings.name()).isEqualTo("unnamed");
        assertThat(settings.maxIterations()).isEqualTo(10);
        assertThat(config.toolRegistry()).isNotNull();
        assertThat(settings.systemPrompt()).isEqualTo("");
        assertThat(config.plugins()).isEmpty();
    }

    @Test
    void shouldBuildWithCustomValues() {
        var tools = new ToolRegistry();
        tools.register(testTool("bash"));
        tools.register(testTool("read"));
        var settings = AgentSettings.builder()
            .name("recherche-agent")
            .modelName("openai/gpt-4o")
            .systemPrompt("Du bist ein Recherche-Agent")
            .maxIterations(15)
            .build();
        var config = AgentConfig.builder()
            .toolRegistry(tools)
            .build();

        assertThat(settings.name()).isEqualTo("recherche-agent");
        assertThat(settings.modelName()).isEqualTo("openai/gpt-4o");
        assertThat(config.toolRegistry().getToolNames()).contains("bash", "read");
        assertThat(settings.systemPrompt()).isEqualTo("Du bist ein Recherche-Agent");
        assertThat(settings.maxIterations()).isEqualTo(15);
    }

    @Test
    void shouldBuildWithConversationManager() {
        var slidingWindow = new SlidingWindowConversationManager(5);
        var config = AgentConfig.builder()
            .conversationManager(slidingWindow)
            .build();

        assertThat(config.conversationManager()).isSameAs(slidingWindow);
        assertThat(config.conversationManager()).isInstanceOf(SlidingWindowConversationManager.class);
    }
}
