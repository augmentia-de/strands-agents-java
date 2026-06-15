package de.augmentia.strandsagents.features.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.agent.ExecutionMetrics;
import de.augmentia.strandsagents.model.agent.StopReason;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HookRegistryTest {

    // --- HookResult ---

    @Test
    void hookResultContinue() {
        var r = new HookResult.Continue();
        assertThat(r).isInstanceOf(HookResult.class);
    }

    @Test
    void hookResultCancel() {
        var r = new HookResult.Cancel("reason");
        assertThat(r.reason()).isEqualTo("reason");
    }

    @Test
    void hookResultModify() {
        var r = new HookResult.Modify<>("changed");
        assertThat(r.value()).isEqualTo("changed");
    }

    @Test
    void hookResultRetry() {
        var r = new HookResult.Retry("retry reason");
        assertThat(r.reason()).isEqualTo("retry reason");
    }

    // --- AgentHook defaults ---

    @Test
    void agentHookDefaultsReturnContinue() {
        var hook = new AgentHook() {
            @Override public String name() { return "test"; }
        };
        assertThat(hook.beforeAgent(null)).isInstanceOf(HookResult.Continue.class);
        assertThat(hook.afterAgent(null, "")).isInstanceOf(HookResult.Continue.class);
        assertThat(hook.beforeModelCall(null)).isInstanceOf(HookResult.Continue.class);
        assertThat(hook.afterModelCall(null, "")).isInstanceOf(HookResult.Continue.class);
        assertThat(hook.beforeToolCall(null)).isInstanceOf(HookResult.Continue.class);
        assertThat(hook.afterToolCall(null, "")).isInstanceOf(HookResult.Continue.class);
    }

    // --- HookContexts ---

    @Test
    void beforeAgentContext() {
        var ctx = new HookContexts.BeforeAgentContext("s1", "hello", Map.of("key", "val"));
        assertThat(ctx.sessionId()).isEqualTo("s1");
        assertThat(ctx.prompt()).isEqualTo("hello");
        assertThat(ctx.contextVariables()).containsEntry("key", "val");
    }

    @Test
    void afterAgentContext() {
        var metrics = new ExecutionMetrics(100, 10, 20, 1);
        var result = new AgentResult("s1", "answer", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        assertThat(ctx.result().finalAnswer()).isEqualTo("answer");
    }

    @Test
    void beforeModelCallContext() {
        var sb = new StringBuilder("sys");
        var ctx = new HookContexts.BeforeModelCallContext("s1", sb, List.of(), List.of(), new ArrayList<>());
        assertThat(ctx.systemPrompt().toString()).isEqualTo("sys");
        assertThat(ctx.messages()).isEmpty();
        assertThat(ctx.tools()).isEmpty();
    }

    @Test
    void afterModelCallContext() {
        var ctx = new HookContexts.AfterModelCallContext("s1", "resp", 10, 20);
        assertThat(ctx.llmResponse()).isEqualTo("resp");
        assertThat(ctx.inputTokens()).isEqualTo(10);
        assertThat(ctx.outputTokens()).isEqualTo(20);
    }

    @Test
    void beforeToolCallContext() {
        var ctx = new HookContexts.BeforeToolCallContext("s1", "calc", Map.of("a", 1));
        assertThat(ctx.toolName()).isEqualTo("calc");
        assertThat(ctx.arguments()).containsEntry("a", 1);
    }

    @Test
    void afterToolCallContext() {
        var ctx = new HookContexts.AfterToolCallContext("s1", "calc", "7", false);
        assertThat(ctx.toolName()).isEqualTo("calc");
        assertThat(ctx.result()).isEqualTo("7");
        assertThat(ctx.isError()).isFalse();
    }

    // --- HookFailurePolicy ---

    @Test
    void hookFailurePolicyValues() {
        assertThat(HookFailurePolicy.values()).containsExactly(
            HookFailurePolicy.CHAIN_ABORT, HookFailurePolicy.ISOLATE);
    }

    // --- HookProvider ---

    @Test
    void hookProviderRegistersHooks() {
        var registry = new HookRegistry();
        var provider = new HookProvider() {
            @Override public String name() { return "test-provider"; }
            @Override public void registerHooks(HookRegistry r) {
                r.register(new AgentHook() {
                    @Override public String name() { return "hook-from-provider"; }
                });
            }
        };
        registry.register(provider);
        assertThat(registry.getHooks()).hasSize(1);
        assertThat(registry.getHooks().get(0).name()).isEqualTo("hook-from-provider");
    }

    @Test
    void nullProviderDoesNotThrow() {
        var registry = new HookRegistry();
        registry.register((HookProvider) null);
        assertThat(registry.getHooks()).isEmpty();
    }

    // --- HookRegistry: register / unregister / clear ---

    @Test
    void registerHook() {
        var registry = new HookRegistry();
        var hook = new TestHook("a");
        registry.register(hook);
        assertThat(registry.getHooks()).containsExactly(hook);
    }

    @Test
    void registerNullHookDoesNotThrow() {
        var registry = new HookRegistry();
        registry.register((AgentHook) null);
        assertThat(registry.getHooks()).isEmpty();
    }

    @Test
    void unregisterByName() {
        var registry = new HookRegistry();
        registry.register(new TestHook("a"));
        registry.register(new TestHook("b"));
        registry.unregister("a");
        assertThat(registry.getHooks()).hasSize(1);
        assertThat(registry.getHooks().get(0).name()).isEqualTo("b");
    }

    @Test
    void unregisterByInstance() {
        var registry = new HookRegistry();
        var a = new TestHook("a");
        registry.register(a);
        registry.unregister(a);
        assertThat(registry.getHooks()).isEmpty();
    }

    @Test
    void clearRemovesAll() {
        var registry = new HookRegistry();
        registry.register(new TestHook("a"));
        registry.register(new TestHook("b"));
        registry.clear();
        assertThat(registry.getHooks()).isEmpty();
    }

    @Test
    void setFailurePolicy() {
        var registry = new HookRegistry();
        assertThat(registry).hasFieldOrPropertyWithValue("failurePolicy", HookFailurePolicy.ISOLATE);
        registry.setFailurePolicy(HookFailurePolicy.CHAIN_ABORT);
        assertThat(registry).hasFieldOrPropertyWithValue("failurePolicy", HookFailurePolicy.CHAIN_ABORT);
    }

    // --- HookRegistry: triggerBeforeAgent ---

    @Test
    void triggerBeforeAgentContinue() {
        var registry = new HookRegistry();
        registry.register(new TestHook("a"));
        var ctx = new HookContexts.BeforeAgentContext("s1", "p", Map.of());
        assertThat(registry.triggerBeforeAgent(ctx))
            .isInstanceOfSatisfying(HookResult.Modify.class, m ->
                assertThat(m.value()).isEqualTo("p"));
    }

    @Test
    void triggerBeforeAgentCancel() {
        var registry = new HookRegistry();
        registry.register(new CancelHook("cancel-agent"));
        var ctx = new HookContexts.BeforeAgentContext("s1", "p", Map.of());
        assertThat(registry.triggerBeforeAgent(ctx))
            .isInstanceOfSatisfying(HookResult.Cancel.class, c ->
                assertThat(c.reason()).contains("cancel-agent"));
    }

    @Test
    void triggerBeforeAgentModifiesPrompt() {
        var registry = new HookRegistry();
        registry.register(new ModifyBeforeAgentHook("mod", "modified prompt"));
        var ctx = new HookContexts.BeforeAgentContext("s1", "original", Map.of());
        assertThat(registry.triggerBeforeAgent(ctx))
            .isInstanceOfSatisfying(HookResult.Modify.class, m ->
                assertThat(m.value()).isEqualTo("modified prompt"));
    }

    @Test
    void triggerBeforeAgentChainsMultipleModifiers() {
        var registry = new HookRegistry();
        registry.register(new ModifyBeforeAgentHook("a", "from a"));
        registry.register(new ModifyBeforeAgentHook("b", "from b"));
        var ctx = new HookContexts.BeforeAgentContext("s1", "original", Map.of());
        assertThat(registry.triggerBeforeAgent(ctx))
            .isInstanceOfSatisfying(HookResult.Modify.class, m ->
                assertThat(m.value()).isEqualTo("from b"));
    }

    @Test
    void triggerBeforeAgentShortCircuitsOnCancel() {
        var registry = new HookRegistry();
        registry.register(new CancelHook("first"));
        registry.register(new TestHook("second"));
        var ctx = new HookContexts.BeforeAgentContext("s1", "p", Map.of());
        assertThat(registry.triggerBeforeAgent(ctx))
            .isInstanceOf(HookResult.Cancel.class);
    }

    // --- HookRegistry: triggerAfterAgent ---

    @Test
    void triggerAfterAgentReturnsModifyWithOriginal() {
        var registry = new HookRegistry();
        registry.register(new TestHook("a"));
        var metrics = new ExecutionMetrics(0, 0, 0, 0);
        var result = new AgentResult("s1", "orig", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        var hookResult = registry.triggerAfterAgent(ctx, "orig");
        assertThat(hookResult).isInstanceOf(HookResult.Modify.class);
        assertThat(((HookResult.Modify<?>) hookResult).value()).isEqualTo("orig");
    }

    @Test
    void triggerAfterAgentModifiesResponse() {
        var registry = new HookRegistry();
        registry.register(new ModifyAfterAgentHook("modifier", "modified!"));
        var metrics = new ExecutionMetrics(0, 0, 0, 0);
        var result = new AgentResult("s1", "orig", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        var hookResult = registry.triggerAfterAgent(ctx, "orig");
        assertThat(((HookResult.Modify<?>) hookResult).value()).isEqualTo("modified!");
    }

    // --- HookRegistry: triggerBeforeModelCall ---

    @Test
    void triggerBeforeModelCallCancel() {
        var registry = new HookRegistry();
        registry.register(new CancelHook("cancel-mc"));
        var sb = new StringBuilder("sys");
        var ctx = new HookContexts.BeforeModelCallContext("s1", sb, List.of(), List.of(), new ArrayList<>());
        assertThat(registry.triggerBeforeModelCall(ctx))
            .isInstanceOfSatisfying(HookResult.Cancel.class, c ->
                assertThat(c.reason()).contains("cancel-mc"));
    }

    @Test
    void triggerBeforeModelCallReturnsToolsWhenNoHooksModify() {
        var registry = new HookRegistry();
        registry.register(new TestHook("a"));
        var sb = new StringBuilder("sys");
        var tools = List.of(ToolSpecification.builder().name("tool1").build());
        var ctx = new HookContexts.BeforeModelCallContext("s1", sb, List.of(), tools, new ArrayList<>());
        var result = registry.triggerBeforeModelCall(ctx);
        assertThat(result).isInstanceOf(HookResult.Modify.class);
        assertThat(((HookResult.Modify<?>) result).value()).isEqualTo(tools);
    }

    @Test
    void triggerBeforeModelCallModifyTools() {
        var registry = new HookRegistry();
        var newTools = List.of(ToolSpecification.builder().name("newTool").build());
        registry.register(new ModifyBeforeModelCallToolHook("mod-tools", newTools));
        var sb = new StringBuilder("sys");
        var ctx = new HookContexts.BeforeModelCallContext("s1", sb, List.of(), List.of(), new ArrayList<>());
        var result = registry.triggerBeforeModelCall(ctx);
        assertThat(result).isInstanceOf(HookResult.Modify.class);
        assertThat(((HookResult.Modify<?>) result).value()).isEqualTo(newTools);
    }

    @Test
    void triggerBeforeModelCallModifySystemPromptInPlace() {
        var registry = new HookRegistry();
        registry.register(new ModifyBeforeModelCallPromptHook("mod-prompt"));
        var sb = new StringBuilder("original");
        var ctx = new HookContexts.BeforeModelCallContext("s1", sb, List.of(), List.of(), new ArrayList<>());
        registry.triggerBeforeModelCall(ctx);
        assertThat(ctx.systemPrompt().toString()).isEqualTo("modified by hook");
    }

    // --- HookRegistry: triggerAfterModelCall ---

    @Test
    void triggerAfterModelCallModify() {
        var registry = new HookRegistry();
        registry.register(new ModifyAfterModelCallHook("modifier", "rewritten"));
        var ctx = new HookContexts.AfterModelCallContext("s1", "original", 5, 10);
        var result = registry.triggerAfterModelCall(ctx, "original");
        assertThat(result).isInstanceOf(HookResult.Modify.class);
        assertThat(((HookResult.Modify<?>) result).value()).isEqualTo("rewritten");
    }

    @Test
    void triggerAfterModelCallRetry() {
        var registry = new HookRegistry();
        registry.register(new RetryAfterModelCallHook("retry-hook", "needs retry"));
        var ctx = new HookContexts.AfterModelCallContext("s1", "bad", 0, 0);
        assertThat(registry.triggerAfterModelCall(ctx, "bad"))
            .isInstanceOfSatisfying(HookResult.Retry.class, r ->
                assertThat(r.reason()).contains("needs retry"));
    }

    @Test
    void triggerAfterModelCallCancel() {
        var registry = new HookRegistry();
        registry.register(new CancelHook("cancel-after-mc"));
        var ctx = new HookContexts.AfterModelCallContext("s1", "resp", 0, 0);
        assertThat(registry.triggerAfterModelCall(ctx, "resp"))
            .isInstanceOf(HookResult.Cancel.class);
    }

    // --- HookRegistry: triggerBeforeToolCall ---

    @Test
    void triggerBeforeToolCallCancel() {
        var registry = new HookRegistry();
        registry.register(new CancelHook("cancel-tc"));
        var ctx = new HookContexts.BeforeToolCallContext("s1", "bash", Map.of());
        assertThat(registry.triggerBeforeToolCall(ctx))
            .isInstanceOf(HookResult.Cancel.class);
    }

    // --- HookRegistry: triggerAfterToolCall ---

    @Test
    void triggerAfterToolCallModify() {
        var registry = new HookRegistry();
        registry.register(new ModifyAfterToolCallHook("modifier", "censored"));
        var ctx = new HookContexts.AfterToolCallContext("s1", "bash", "secret data", false);
        var result = registry.triggerAfterToolCall(ctx, "secret data");
        assertThat(((HookResult.Modify<?>) result).value()).isEqualTo("censored");
    }

    @Test
    void triggerAfterToolCallPreservesOnContinue() {
        var registry = new HookRegistry();
        registry.register(new TestHook("pass-through"));
        var ctx = new HookContexts.AfterToolCallContext("s1", "calc", "7", false);
        var result = registry.triggerAfterToolCall(ctx, "7");
        assertThat(((HookResult.Modify<?>) result).value()).isEqualTo("7");
    }

    // --- Hook chaining ---

    @Test
    void multipleHooksChainModifications() {
        var registry = new HookRegistry();
        registry.register(new ModifyAfterAgentHook("first", "step1 "));
        registry.register(new ModifyAfterAgentHook("second", "step2 "));
        var metrics = new ExecutionMetrics(0, 0, 0, 0);
        var result = new AgentResult("s1", "orig", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        var hookResult = registry.triggerAfterAgent(ctx, "orig");
        assertThat(((HookResult.Modify<?>) hookResult).value()).isEqualTo("step2 ");
    }

    // --- Failure isolation ---

    @Test
    void isolatedHookFailureDoesNotAbortChain() {
        var registry = new HookRegistry();
        registry.setFailurePolicy(HookFailurePolicy.ISOLATE);
        registry.register(new ThrowingHook("throws"));
        registry.register(new ModifyAfterAgentHook("ok", "survived"));

        var metrics = new ExecutionMetrics(0, 0, 0, 0);
        var result = new AgentResult("s1", "orig", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        var hookResult = registry.triggerAfterAgent(ctx, "orig");
        assertThat(((HookResult.Modify<?>) hookResult).value()).isEqualTo("survived");
    }

    @Test
    void chainAbortOnHookFailure() {
        var registry = new HookRegistry();
        registry.setFailurePolicy(HookFailurePolicy.CHAIN_ABORT);
        registry.register(new ThrowingHook("throws"));
        registry.register(new ModifyAfterAgentHook("ok", "never-reached"));

        var metrics = new ExecutionMetrics(0, 0, 0, 0);
        var result = new AgentResult("s1", "orig", List.of(), metrics, StopReason.COMPLETED);
        var ctx = new HookContexts.AfterAgentContext("s1", result);
        var hookResult = registry.triggerAfterAgent(ctx, "orig");
        assertThat(hookResult).isInstanceOfSatisfying(HookResult.Cancel.class, c ->
            assertThat(c.reason()).contains("throws"));
    }

    // --- Test hook implementations ---

    private static class TestHook implements AgentHook {
        private final String name;
        TestHook(String name) { this.name = name; }
        @Override public String name() { return name; }
    }

    private static class ModifyBeforeAgentHook implements AgentHook {
        private final String name;
        private final String newPrompt;
        ModifyBeforeAgentHook(String name, String newPrompt) { this.name = name; this.newPrompt = newPrompt; }
        @Override public String name() { return name; }
        @Override public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            return new HookResult.Modify<>(newPrompt);
        }
    }

    private static class CancelHook implements AgentHook {
        private final String name;
        CancelHook(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            return new HookResult.Cancel(name + " cancelled");
        }
        @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            return new HookResult.Cancel(name + " cancelled");
        }
        @Override public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            return new HookResult.Cancel(name + " cancelled");
        }
        @Override public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
            return new HookResult.Cancel(name + " cancelled");
        }
        @Override public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
            return new HookResult.Cancel(name + " cancelled");
        }
    }

    private static class ModifyAfterAgentHook implements AgentHook {
        private final String name;
        private final String newValue;
        ModifyAfterAgentHook(String name, String newValue) { this.name = name; this.newValue = newValue; }
        @Override public String name() { return name; }
        @Override public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
            return new HookResult.Modify<>(newValue);
        }
    }

    private static class ModifyAfterModelCallHook implements AgentHook {
        private final String name;
        private final String newValue;
        ModifyAfterModelCallHook(String name, String newValue) { this.name = name; this.newValue = newValue; }
        @Override public String name() { return name; }
        @Override public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            return new HookResult.Modify<>(newValue);
        }
    }

    private static class RetryAfterModelCallHook implements AgentHook {
        private final String name;
        private final String reason;
        RetryAfterModelCallHook(String name, String reason) { this.name = name; this.reason = reason; }
        @Override public String name() { return name; }
        @Override public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            return new HookResult.Retry(reason);
        }
    }

    private static class ModifyBeforeModelCallToolHook implements AgentHook {
        private final String name;
        private final List<ToolSpecification> newTools;
        ModifyBeforeModelCallToolHook(String name, List<ToolSpecification> newTools) {
            this.name = name; this.newTools = newTools;
        }
        @Override public String name() { return name; }
        @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            return new HookResult.Modify<>(newTools);
        }
    }

    private static class ModifyBeforeModelCallPromptHook implements AgentHook {
        private final String name;
        ModifyBeforeModelCallPromptHook(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            ctx.systemPrompt().setLength(0);
            ctx.systemPrompt().append("modified by hook");
            return new HookResult.Continue();
        }
    }

    private static class ModifyAfterToolCallHook implements AgentHook {
        private final String name;
        private final String newValue;
        ModifyAfterToolCallHook(String name, String newValue) { this.name = name; this.newValue = newValue; }
        @Override public String name() { return name; }
        @Override public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
            return new HookResult.Modify<>(newValue);
        }
    }

    private static class ThrowingHook implements AgentHook {
        private final String name;
        ThrowingHook(String name) { this.name = name; }
        @Override public String name() { return name; }
        @Override public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
            throw new RuntimeException("hook failed");
        }
    }
}
