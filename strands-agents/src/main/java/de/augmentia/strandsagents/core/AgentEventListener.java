package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.model.event.AgentEvent;

@FunctionalInterface
/**
 * Functional interface for consuming agent lifecycle events.
 */
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
