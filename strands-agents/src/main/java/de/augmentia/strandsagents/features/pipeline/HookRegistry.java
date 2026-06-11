package de.augmentia.strandsagents.features.pipeline;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HookRegistry {

    private final List<AgentHook> hooks = new CopyOnWriteArrayList<>();
    private HookFailurePolicy failurePolicy = HookFailurePolicy.ISOLATE;

    public void register(AgentHook hook) {
        if (hook != null) hooks.add(hook);
    }

    public void register(HookProvider provider) {
        if (provider != null) provider.registerHooks(this);
    }

    public void unregister(String name) {
        hooks.removeIf(h -> h.name().equals(name));
    }

    public void unregister(AgentHook hook) {
        hooks.remove(hook);
    }

    public void clear() {
        hooks.clear();
    }

    public List<AgentHook> getHooks() {
        return List.copyOf(hooks);
    }

    public void setFailurePolicy(HookFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    private List<AgentHook> getSortedHooks() {
        var sorted = new ArrayList<>(hooks);
        sorted.sort(Comparator.comparingInt(AgentHook::order));
        return List.copyOf(sorted);
    }

    // --- triggers ---

    public HookResult triggerBeforeAgent(HookContexts.BeforeAgentContext ctx) {
        var currentPrompt = ctx.prompt();
        for (var hook : getSortedHooks()) {
            var result = wrap(() -> hook.beforeAgent(ctx), hook.name());
            switch (result) {
                case HookResult.Modify<?> m -> currentPrompt = (String) m.value();
                case HookResult.Cancel ignored -> { return result; }
                default -> {}
            }
        }
        return new HookResult.Modify<>(currentPrompt);
    }

    public HookResult triggerAfterAgent(HookContexts.AfterAgentContext ctx, String response) {
        var current = response;
        for (var hook : getSortedHooks()) {
            final var input = current;
            var result = wrap(() -> hook.afterAgent(ctx, input), hook.name());
            if (result instanceof HookResult.Modify<?>(Object value)) {
                current = (String) value;
            } else if (!(result instanceof HookResult.Continue)) {
                return result;
            }
        }
        return new HookResult.Modify<>(current);
    }

    public HookResult triggerBeforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        var currentTools = ctx.tools();
        for (var hook : getSortedHooks()) {
            var result = wrap(() -> hook.beforeModelCall(ctx), hook.name());
            switch (result) {
                case HookResult.Modify<?> m -> {
                    if (m.value() instanceof List<?> list) {
                        @SuppressWarnings("unchecked")
                        var specs = (List<ToolSpecification>) list;
                        currentTools = specs;
                    }
                }
                case HookResult.Cancel ignored -> { return result; }
                default -> {}
            }
        }
        return new HookResult.Modify<>(currentTools);
    }

    public HookResult triggerAfterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
        var current = response;
        for (var hook : getSortedHooks()) {
            final var input = current;
            var result = wrap(() -> hook.afterModelCall(ctx, input), hook.name());
            switch (result) {
                case HookResult.Modify<?> m -> current = (String) m.value();
                case HookResult.Retry ignored -> { return result; }
                case HookResult.Cancel ignored -> { return result; }
                default -> {}
            }
        }
        return new HookResult.Modify<>(current);
    }

    public HookResult triggerBeforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        for (var hook : getSortedHooks()) {
            var result = wrap(() -> hook.beforeToolCall(ctx), hook.name());
            if (!(result instanceof HookResult.Continue)) return result;
        }
        return new HookResult.Continue();
    }

    public HookResult triggerAfterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
        var current = result;
        for (var hook : getSortedHooks()) {
            final var input = current;
            var r = wrap(() -> hook.afterToolCall(ctx, input), hook.name());
            if (r instanceof HookResult.Modify<?>(Object value)) {
                current = (String) value;
            } else if (!(r instanceof HookResult.Continue)) {
                return r;
            }
        }
        return new HookResult.Modify<>(current);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private HookResult wrap(ThrowingSupplier<HookResult> fn, String hookName) {
        try {
            return fn.get();
        } catch (Exception e) {
            if (failurePolicy == HookFailurePolicy.CHAIN_ABORT) {
                return new HookResult.Cancel("Hook '" + hookName + "' failed: " + e.getMessage());
            }
            return new HookResult.Continue();
        }
    }
}
