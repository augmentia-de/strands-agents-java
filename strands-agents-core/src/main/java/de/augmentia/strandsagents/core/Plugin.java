package de.augmentia.strandsagents.core;

import java.util.List;

public interface Plugin {
    String name();
    default void initAgent(StrandsAgent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() {
        return List.of();
    }
}
