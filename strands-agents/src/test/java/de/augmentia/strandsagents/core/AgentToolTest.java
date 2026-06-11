package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.subagent.SubAgentTool;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentToolTest {

    @Test
    void shouldExecuteSubAgent() {
        var subAgent = new Agent(new MockChatModel("Sub-Antwort: %s"));
        var tool = new SubAgentTool(subAgent, "recherche", "Recherchiert Informationen");

        var result = tool.execute("id", new SubAgentTool.Params("Wie ist das Wetter?"), new AtomicBoolean(false), null);

        assertThat(result.content()).anyMatch(c -> c.toString().contains("Sub-Antwort"));
    }

    @Test
    void shouldRespectRecursionDepth() {
        var inner = new Agent(new MockChatModel("Inner: %s"));
        var tool = new SubAgentTool(inner, "nested");

        var result = tool.execute("id", new SubAgentTool.Params("Ebene 1"), new AtomicBoolean(false), null);
        assertThat(result.content()).noneMatch(c -> c.toString().contains("Rekursionstiefe"));
    }

    @Test
    void shouldBeRegistrableInToolRegistry() {
        var subAgent = new Agent(new MockChatModel());
        var tool = new SubAgentTool(subAgent, "recherche");

        var registry = new ToolRegistry();
        registry.register(tool);

        assertThat(registry.getToolNames()).contains("recherche");
    }
}
