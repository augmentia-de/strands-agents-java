package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Interface for tools executable by an agent, with typed parameters and async support.
 */
public interface AgentTool<P> {
    ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    String name();
    String description();
    Class<P> parameterType();
    JsonNode parameterSchema();
    /**
     * Executes this tool with the given parameters, supporting abort and progress updates.
     *
     * @param toolCallId identifier for this tool invocation
     * @param params the typed parameters for execution
     * @param abortFlag flag to signal abort mid-execution
     * @param onUpdate consumer for intermediate result updates
     */
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception;

    /**
     * Executes this tool without progress updates.
     */
    default ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag) throws Exception {
        return execute(toolCallId, params, abortFlag, null);
    }

    /**
     * Executes this tool asynchronously on a virtual thread.
     */
    default CompletableFuture<ToolResult> executeAsync(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(toolCallId, params, abortFlag, onUpdate);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, VIRTUAL_EXECUTOR);
    }

    /**
     * Strips leading slashes to produce a relative path.
     */
    public static String relativePath(String path) {
        String agentPath = path;
        if (agentPath.startsWith("/")) {
            agentPath = agentPath.substring(1); // wird zu "src/App.java"
            return relativePath(agentPath);
        }
        return agentPath;
    }
}