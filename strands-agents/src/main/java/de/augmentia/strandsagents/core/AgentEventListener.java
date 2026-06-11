package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.model.event.AgentEvent;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);
}
