package de.augmentia.strandsagents.interceptor.pipeline;

/**
 * Policy for handling hook execution failures.
 */
public enum HookFailurePolicy {
    CHAIN_ABORT,
    ISOLATE
}
