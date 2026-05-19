package de.augmentia.strandsagents.core.plugin;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.StrandsAgent;

import java.util.List;

public interface Plugin {
    String name();
    default void initAgent(StrandsAgent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() {
        return List.of();
    }
}
