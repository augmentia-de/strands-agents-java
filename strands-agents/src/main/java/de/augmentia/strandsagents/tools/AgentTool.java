package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface AgentTool<P> {
    ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    String name();
    String description();
    Class<P> parameterType();
    JsonNode parameterSchema();
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception;

    default ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag) throws Exception {
        return execute(toolCallId, params, abortFlag, null);
    }

    default CompletableFuture<ToolResult> executeAsync(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(toolCallId, params, abortFlag, onUpdate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, VIRTUAL_EXECUTOR);
    }

    public static String relativePath(String path) {
        String agentPath = path;
        if (agentPath.startsWith("/")) {
            agentPath = agentPath.substring(1); // wird zu "src/App.java"
            return relativePath(agentPath);
        }
        return agentPath;
    }
}