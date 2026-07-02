package de.augmentia.strandsagents.interceptor.telemetry;

import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.model.event.AgentEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HookRegistry implements AgentEventListener {

    private final List<RegisteredHook> hooks = new CopyOnWriteArrayList<>();
    private volatile AgentEventListener downstream;

    public void registerHook(String name, AgentEventFilter filter, AgentEventListener hook) {
        hooks.add(new RegisteredHook(name, filter, hook));
    }

    public void registerHook(String name, AgentEventListener hook) {
        registerHook(name, e -> true, hook);
    }

    public void setDownstream(AgentEventListener downstream) {
        this.downstream = downstream;
    }

    @Override
    public void onEvent(AgentEvent event) {
        for (var hook : hooks) {
            try {
                if (hook.filter().matches(event)) {
                    hook.hook().onEvent(event);
                }
            } catch (Exception e) {
                // isolate hook failures
            }
        }
        if (downstream != null) {
            downstream.onEvent(event);
        }
    }

    public List<RegisteredHook> getHooks() {
        return List.copyOf(hooks);
    }

    public void clear() {
        hooks.clear();
    }

    public record RegisteredHook(String name, AgentEventFilter filter, AgentEventListener hook) {}
}
