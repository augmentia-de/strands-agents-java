package de.augmentia.strandsagents.interceptor.pipeline;



/**
 * Provider that registers one or more hooks into a {@link HookRegistry}.
 */
public interface HookProvider {
    String name();
    void registerHooks(HookRegistry registry);
}
