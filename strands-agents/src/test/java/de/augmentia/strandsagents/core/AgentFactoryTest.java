package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.config.StrandsAgentConfig;
import de.augmentia.strandsagents.features.conversation.ConversationManager;
import de.augmentia.strandsagents.features.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.tools.ListToolsTool;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createToolRegistry_withMinimalConfig() {
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", false, true, "", "", "");
        var registry = AgentFactory.createToolRegistry(config);
        assertThat(registry).isNotNull();
        assertThat(registry.getToolNames()).isNotEmpty();
        assertThat(registry.getToolNames()).contains("list_tools");
    }

    @Test
    void createToolRegistry_withBashAllowed() {
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", true, true, "", "", "");
        var registry = AgentFactory.createToolRegistry(config);
        assertThat(registry.getToolNames()).contains("bash");
    }

    @Test
    void createToolRegistry_extraTools() {
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", false, true,
            "de.augmentia.strandsagents.features.tools.CalculatorTool", "", "");
        var registry = AgentFactory.createToolRegistry(config);
        assertThat(registry.getToolNames()).contains("add");
    }

    @Test
    void createCheckpointService_withDefaultChannel() {
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", false, true, "", "", "");
        var svc = AgentFactory.createCheckpointService(config);
        assertThat(svc).isNotNull();
    }

    @Test
    void createSessionManager_createsDirectory(@TempDir Path tmp) {
        var sessionDir = tmp.resolve(".sessions");
        var sm = AgentFactory.createSessionManager(sessionDir);
        assertThat(sm).isNotNull();
        assertThat(sessionDir).isDirectory();
    }

    @Test
    void createConversationManager_withValidSize() {
        var cm = AgentFactory.createConversationManager(5);
        assertThat(cm).isNotNull();
        assertThat(cm).isInstanceOf(ConversationManager.class);
    }

    @Test
    void buildPlugins_withSkillsList() {
        var plugins = AgentFactory.buildPlugins(List.of(), List.of("default"), true);
        assertThat(plugins).isNotEmpty();
        assertThat(plugins).anyMatch(p -> p instanceof GuardrailPlugin);
    }

    @Test
    void buildPlugins_empty() {
        var plugins = AgentFactory.buildPlugins();
        assertThat(plugins).isNotEmpty();
    }

    @Test
    void sortPlugins_ordersByOrder() {
        var p1 = new Plugin() {
            @Override public int order() { return 10; }
            @Override public String name() { return "second"; }
        };
        var p2 = new Plugin() {
            @Override public int order() { return 5; }
            @Override public String name() { return "first"; }
        };
        var sorted = AgentFactory.sortPlugins(new java.util.ArrayList<>(List.of(p1, p2)));
        assertThat(sorted.get(0).name()).isEqualTo("first");
        assertThat(sorted.get(1).name()).isEqualTo("second");
    }

    @Test
    void createAgent_createsExecutableAgent() throws Exception {
        var model = new MockChatModel("Mock: %s");
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", false, true, "", "", "");
        var registry = AgentFactory.createToolRegistry(config);
        var plugins = AgentFactory.buildPlugins();
        var agent = AgentFactory.createAgent(model, registry, null, null, plugins);
        assertThat(agent).isNotNull();

        var result = agent.execute("test");
        assertThat(result.finalAnswer()).isEqualTo("Mock: test");
        assertThat(result.stopReason()).isNotNull();
    }

    @Test
    void createToolRegistry_httpToolRegistered() {
        var config = new StrandsAgentConfig(
            "skills", ".sessions", true, "logs/llm.log", List.of(),
            true, true, "config.json", "", false, false, "", "", "");
        var registry = AgentFactory.createToolRegistry(config);
        assertThat(registry.getToolNames()).contains("get");
    }
}
