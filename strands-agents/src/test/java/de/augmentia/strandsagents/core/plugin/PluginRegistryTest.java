package de.augmentia.strandsagents.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.model.event.BeforeInvocationEvent;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.ToolResult;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {

    private static AgentTool<?> testTool(String name) {
        return new AgentTool<Object>() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public Class<Object> parameterType() { return Object.class; }
            @Override public com.fasterxml.jackson.databind.node.ObjectNode parameterSchema() {
                return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            }
            @Override
            public ToolResult execute(String toolCallId, Object params, AtomicBoolean abortFlag,
                                       java.util.function.Consumer<ToolResult> onUpdate) {
                return ToolResult.success("ok");
            }
        };
    }

    @Test
    void initializeCallsInitAgentOnEachPlugin() {
        var called = new boolean[]{false};
        var plugin = new Plugin() {
            @Override public String name() { return "test-plugin"; }
            @Override public void initAgent(Agent agent) { called[0] = true; }
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var agent = new Agent(new MockChatModel());
        registry.initialize(agent);
        assertThat(called[0]).isTrue();
    }

    @Test
    void initializeRegistersToolsFromPlugins() {
        var plugin = new Plugin() {
            @Override public String name() { return "tool-plugin"; }
            @Override public void initAgent(Agent agent) {}
            @Override public List<ToolRegistry.ToolMethod> getTools() {
                return List.of(ToolRegistry.createMethod(testTool("my-tool")));
            }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var agent = new Agent(new MockChatModel());
        registry.initialize(agent);
        assertThat(agent.getToolRegistry().getToolNames()).contains("my-tool");
    }

    @Test
    void initializeHandlesMultiplePlugins() {
        var initOrder = new StringBuilder();
        var pluginA = new Plugin() {
            @Override public String name() { return "A"; }
            @Override public void initAgent(Agent agent) { initOrder.append("A"); }
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var pluginB = new Plugin() {
            @Override public String name() { return "B"; }
            @Override public void initAgent(Agent agent) { initOrder.append("B"); }
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var registry = new PluginRegistry(List.of(pluginA, pluginB));
        var agent = new Agent(new MockChatModel());
        registry.initialize(agent);
        assertThat(initOrder.toString()).isEqualTo("AB");
    }

    @Test
    void pluginCanSetSystemPromptViaHook() {
        var plugin = new Plugin() {
            @Override public String name() { return "prompt-plugin"; }
            @Override public void initAgent(Agent agent) {
                agent.setPluginHook(sb -> sb.append("\n<!-- added-by-plugin -->"));
            }
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var agent = new Agent(new MockChatModel());
        agent.setSystemPrompt("Base prompt");
        registry.initialize(agent);

        agent.execute("test");

        assertThat(agent.getSystemPrompt()).contains("<!-- added-by-plugin -->");
    }

    @Test
    void beforeInvocationHookReceivesEvent() {
        var plugin = new Plugin() {
            @Override public String name() { return "hook-plugin"; }
            @Override public void initAgent(Agent agent) {}
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var captured = new StringBuilder[]{new StringBuilder()};
        registry.registerBeforeInvocationHook(event -> {
            captured[0] = event.systemPrompt();
        });

        var agent = new Agent(new MockChatModel());
        agent.setSystemPrompt("Custom prompt");
        registry.initialize(agent);
        agent.setEventListener(registry);

        agent.execute("hello");

        assertThat(captured[0].toString()).contains("Custom prompt");
    }

    @Test
    void onEventIgnoresNonBeforeInvocation() {
        var plugin = new Plugin() {
            @Override public String name() { return "test"; }
            @Override public void initAgent(Agent agent) {}
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var called = new boolean[]{false};
        registry.registerBeforeInvocationHook(event -> called[0] = true);

        var agent = new Agent(new MockChatModel());
        registry.initialize(agent);
        agent.setEventListener(registry);

        agent.execute("hello");

        assertThat(called[0]).isTrue();
    }
}
