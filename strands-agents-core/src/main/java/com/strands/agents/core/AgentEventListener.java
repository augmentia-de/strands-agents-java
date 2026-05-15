package com.strands.agents.core;

import com.strands.agents.core.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
