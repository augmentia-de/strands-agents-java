package de.augmentia.strandsagents.interceptor.plugin;

import de.augmentia.strandsagents.core.Agent;

import java.util.*;

public class PluginRegistry {

    private final List<Plugin> plugins;

    public PluginRegistry(List<Plugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

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
