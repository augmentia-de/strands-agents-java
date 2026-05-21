package de.augmentia.strandsagents.core.hook;

public interface HookProvider {
    String name();
    void registerHooks(HookRegistry registry);
}
