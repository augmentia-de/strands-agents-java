package de.augmentia.strandsagents.features.pipeline;

public interface HookProvider {
    String name();
    void registerHooks(HookRegistry registry);
}
