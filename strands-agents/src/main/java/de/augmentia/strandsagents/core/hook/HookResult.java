package de.augmentia.strandsagents.core.hook;

public sealed interface HookResult
    permits HookResult.Continue, HookResult.Cancel, HookResult.Modify, HookResult.Retry {

    record Continue() implements HookResult {}

    record Cancel(String reason) implements HookResult {}

    record Modify<T>(T value) implements HookResult {}

    record Retry(String reason) implements HookResult {}
}
