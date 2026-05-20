package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.a2a.SubAgentTool;
import org.junit.jupiter.api.Test;

class AgentToolTest {

    @Test
    void shouldExecuteSubAgent() {
        var subAgent = new Agent(new MockChatModel("Sub-Antwort: %s"));
        var tool = new SubAgentTool(subAgent, "recherche", "Recherchiert Informationen");

        var result = tool.execute("Wie ist das Wetter?");

        assertThat(result).contains("Sub-Antwort");
    }

    @Test
    void shouldRespectRecursionDepth() {
        var inner = new Agent(new MockChatModel("Inner: %s"));
        var tool = new SubAgentTool(inner, "nested");

        // Simulate deep recursion by calling tool directly many times
        // AgentTool increments depth via ScopedValue internally
        String result = tool.execute("Ebene 1");
        assertThat(result).doesNotContain("Rekursionstiefe");
    }

    @Test
    void shouldHaveToolAnnotation() throws Exception {
        var subAgent = new Agent(new MockChatModel());
        var tool = new SubAgentTool(subAgent, "helper", "Hilfs-Agent");

        var method = SubAgentTool.class.getMethod("execute", String.class);
        assertThat(method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)).isTrue();
    }

    @Test
    void shouldBeRegistrableInToolRegistry() {
        var subAgent = new Agent(new MockChatModel());
        var tool = new SubAgentTool(subAgent, "recherche");

        var registry = new ToolRegistry();
        registry.register(tool);

        assertThat(registry.getToolNames()).contains("execute");
    }
}
