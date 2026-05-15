package com.strands.agents.telemetry;

import com.strands.agents.core.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventFilter {
    boolean matches(AgentEvent event);
}
