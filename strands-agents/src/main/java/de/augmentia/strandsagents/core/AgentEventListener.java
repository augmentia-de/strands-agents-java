package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
