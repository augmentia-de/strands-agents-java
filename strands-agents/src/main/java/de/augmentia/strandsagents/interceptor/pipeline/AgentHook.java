package de.augmentia.strandsagents.interceptor.pipeline;

public interface AgentHook {

    String name();

    default int order() { return 0; }

    default HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
        return new HookResult.Continue();
    }

    default HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
        return new HookResult.Continue();
    }

    default HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
        return new HookResult.Continue();
    }

    default HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse) {
        return new HookResult.Continue();
    }

    default HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        return new HookResult.Continue();
    }

    default HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String toolResult) {
        return new HookResult.Continue();
    }
}
