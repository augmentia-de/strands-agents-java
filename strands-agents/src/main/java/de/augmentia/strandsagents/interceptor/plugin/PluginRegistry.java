package de.augmentia.strandsagents.interceptor.plugin;

import de.augmentia.strandsagents.core.Agent;

import java.util.*;

/**
 * Registry that initializes and registers all plugins with an agent.
 */
public class PluginRegistry {

    private final List<Plugin> plugins;

    /**
     * @param plugins the plugins to manage; the list is defensively copied
     */
    public PluginRegistry(List<Plugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

    /**
     * Initialises each plugin with the given agent and registers its hooks and tools.
     */
    public void initialize(Agent agent) {
        var hookRegistry = agent.getHookRegistry();
        for (var plugin : plugins) {
            plugin.initAgent(agent);
            for (var toolMethod : plugin.getTools()) {
                agent.getToolRegistry().register(
                    toolMethod.spec().name(),
                    toolMethod.spec(),
                    toolMethod
                );
            }
            hookRegistry.register(plugin);
        }
    }
}
