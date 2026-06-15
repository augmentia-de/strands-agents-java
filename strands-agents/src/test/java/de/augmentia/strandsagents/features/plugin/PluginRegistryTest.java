package de.augmentia.strandsagents.features.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.ToolResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
    void pluginCanModifyEffectivePromptWithoutChangingField() {
        var plugin = new Plugin() {
            @Override public String name() { return "prompt-plugin"; }
            @Override public void initAgent(Agent agent) {}
            @Override public List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                ctx.systemPrompt().append("\n<!-- added-by-plugin -->");
                return new HookResult.Continue();
            }
        };
        var registry = new PluginRegistry(List.of(plugin));
        var agent = new Agent(new MockChatModel());
        agent.setSystemPrompt("Base prompt");
        registry.initialize(agent);

        agent.execute("test");

        // Hook modifications no longer persist to the field (cache-friendly)
        assertThat(agent.getSystemPrompt()).isEqualTo("Base prompt");
    }

    @Test
    void beforeInvocationHookReceivesEvent() {
        var captured = new StringBuilder[]{new StringBuilder()};
        var agent = new Agent(new MockChatModel());
        agent.setSystemPrompt("Custom prompt");
        agent.addHook(new AgentHook() {
            @Override public String name() { return "capture-hook"; }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                captured[0] = ctx.systemPrompt();
                return new HookResult.Continue();
            }
        });

        agent.execute("hello");

        assertThat(captured[0].toString()).contains("Custom prompt");
    }

    @Test
    void onEventIgnoresNonBeforeInvocation() {
        var called = new boolean[]{false};
        var agent = new Agent(new MockChatModel());
        agent.addHook(new AgentHook() {
            @Override public String name() { return "test-hook"; }
            @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                called[0] = true;
                return new HookResult.Continue();
            }
        });

        agent.execute("hello");

        assertThat(called[0]).isTrue();
    }
}
