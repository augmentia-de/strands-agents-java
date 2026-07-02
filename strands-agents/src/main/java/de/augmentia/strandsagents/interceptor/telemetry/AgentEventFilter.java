package de.augmentia.strandsagents.interceptor.telemetry;

import de.augmentia.strandsagents.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventFilter {
    boolean matches(AgentEvent event);
}
