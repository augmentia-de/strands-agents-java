package de.augmentia.strandsagents.features.telemetry;

import de.augmentia.strandsagents.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventFilter {
    boolean matches(AgentEvent event);
}
