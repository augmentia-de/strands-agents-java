package de.augmentia.strandsagents.features.plugin;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.guardrails.BlockAction;
import de.augmentia.strandsagents.features.guardrails.Guardrail;

import java.util.List;

public interface Plugin extends AgentHook {
    String name();
    default int order() { return 0; }
    default void initAgent(Agent agent) {}
    default void onDestroy() {}
    default List<ToolRegistry.ToolMethod> getTools() {
        return List.of();
    }
    default List<Guardrail> getInputGuardrails() {
        return List.of();
    }
    default List<Guardrail> getOutputGuardrails() {
        return List.of();
    }
    default BlockAction getBlockAction() {
        return BlockAction.THROW;
    }
    default String getFallbackMessage() {
        return "Request blocked";
    }
}
