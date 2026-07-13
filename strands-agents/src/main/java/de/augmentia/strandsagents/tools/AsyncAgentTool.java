package de.augmentia.strandsagents.tools;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface AsyncAgentTool<P> extends AgentTool<P> {
    CompletableFuture<ToolResult> executeAsync(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate);
}