package de.augmentia.strandsagents.telemetry;

import de.augmentia.strandsagents.core.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventFilter {
    boolean matches(AgentEvent event);
}
