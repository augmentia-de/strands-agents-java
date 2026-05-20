package de.augmentia.strandsagents.core.plugin;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;

import java.util.List;

public interface Plugin {
    String name();
    default void initAgent(Agent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() {
        return List.of();
    }
}
