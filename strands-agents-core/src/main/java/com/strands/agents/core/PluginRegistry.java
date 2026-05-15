package com.strands.agents.core;

import com.strands.agents.core.model.event.*;
import java.util.*;

public class PluginRegistry implements AgentEventListener {

    private final List<Plugin> plugins;
    private final List<BeforeInvocationHook> beforeInvocationHooks = new ArrayList<>();

    public PluginRegistry(List<Plugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

    @FunctionalInterface
    public interface BeforeInvocationHook {
        void onBeforeInvocation(BeforeInvocationEvent event);
    }

    public void registerBeforeInvocationHook(BeforeInvocationHook hook) {
        beforeInvocationHooks.add(hook);
    }

    public void initialize(StrandsAgent agent) {
        for (var plugin : plugins) {
            plugin.initAgent(agent);
            for (var toolMethod : plugin.getTools()) {
                agent.getToolRegistry().register(
                    toolMethod.spec().name(),
                    toolMethod.spec(),
                    toolMethod
                );
            }
        }
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event instanceof BeforeInvocationEvent bie) {
            for (var hook : beforeInvocationHooks) {
                hook.onBeforeInvocation(bie);
            }
        }
    }
}
