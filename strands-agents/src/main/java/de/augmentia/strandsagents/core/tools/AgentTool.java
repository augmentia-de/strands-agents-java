package de.augmentia.strandsagents.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public interface AgentTool<P> {
    String name();
    String description();
    Class<P> parameterType();
    JsonNode parameterSchema();
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception;

    default ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag) throws Exception {
        return execute(toolCallId, params, abortFlag, null);
    }
}
