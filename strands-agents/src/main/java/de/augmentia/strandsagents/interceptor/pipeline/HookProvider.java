package de.augmentia.strandsagents.interceptor.pipeline;



public interface HookProvider {
    String name();
    void registerHooks(HookRegistry registry);
}
