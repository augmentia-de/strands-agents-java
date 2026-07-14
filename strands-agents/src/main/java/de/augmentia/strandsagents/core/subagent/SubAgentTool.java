package de.augmentia.strandsagents.core.subagent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.context.AgentContext;
import de.augmentia.strandsagents.tools.AsyncAgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A tool that delegates execution to a sub-agent. */
public class SubAgentTool implements AsyncAgentTool<SubAgentTool.Params> {

    public static final int MAX_RECURSION_DEPTH = 5;
    private static final ThreadLocal<Integer> RECURSION_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final Agent subAgent;
    private final String toolName;
    private final String description;
    private final SubAgentExecutor executor;

    /** Creates a SubAgentTool with a default executor. */
    public SubAgentTool(Agent subAgent, String toolName, String description) {
        this(subAgent, toolName, description, new SubAgentExecutor());
    }

    /** Creates a SubAgentTool with the given executor. */
    public SubAgentTool(Agent subAgent, String toolName, String description, SubAgentExecutor executor) {
        this.subAgent = subAgent;
        this.toolName = toolName;
        this.description = description;
        this.executor = executor;
    }

    /** Creates a SubAgentTool with a default description. */
    public SubAgentTool(Agent subAgent, String toolName) {
        this(subAgent, toolName, "Executes a specialized sub-agent: " + toolName);
    }

    /** Creates a SubAgentTool with a default description and the given executor. */
    public SubAgentTool(Agent subAgent, String toolName, SubAgentExecutor executor) {
        this(subAgent, toolName, "Executes a specialized sub-agent: " + toolName, executor);
    }

    /** Returns the tool name. */
    @Override
    public String name() {
        return toolName;
    }

    /** Returns the tool description. */
    @Override
    public String description() {
        return description;
    }

    /** Returns the parameter type class. */
    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    /** Returns the JSON schema for the tool parameters. */
    @Override
    public ObjectNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();
        var promptProp = factory.objectNode();
        promptProp.put("type", "string");
        promptProp.put("description", "The prompt to delegate to the sub-agent");
        props.set("prompt", promptProp);
        schema.set("properties", props);
        var required = factory.arrayNode();
        required.add("prompt");
        schema.set("required", required);
        return schema;
    }

    /** Executes the sub-agent synchronously. */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        int currentDepth = RECURSION_DEPTH.get();
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return ToolResult.error("Maximum recursion depth of " + MAX_RECURSION_DEPTH + " reached.");
        }
        RECURSION_DEPTH.set(currentDepth + 1);
        try {
            var sessionId = AgentContext.SESSION_ID.get();
            SubAgentResult a2aResult = sessionId != null
                ? executor.call(subAgent, params.prompt(), toolName, sessionId)
                : executor.call(subAgent, params.prompt(), toolName);
            return ToolResult.success(a2aResult.result());
        } catch (Exception e) {
            return ToolResult.error("Error in sub-agent: " + e.getMessage());
        } finally {
            RECURSION_DEPTH.set(currentDepth);
        }
    }

    /** Executes the sub-agent asynchronously. */
    @Override
    public CompletableFuture<ToolResult> executeAsync(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        int currentDepth = RECURSION_DEPTH.get();
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return CompletableFuture.completedFuture(
                ToolResult.error("Maximum recursion depth of " + MAX_RECURSION_DEPTH + " reached."));
        }

        var sessionId = AgentContext.SESSION_ID.get();
        RECURSION_DEPTH.set(currentDepth + 1);

        CompletableFuture<SubAgentResult> futureResult;
        try {
            futureResult = sessionId != null
                ? executor.callAsync(subAgent, params.prompt(), toolName, sessionId)
                : executor.callAsync(subAgent, params.prompt(), toolName);
        } finally {
            RECURSION_DEPTH.set(currentDepth);
        }

        return futureResult
            .thenApply(a2aResult -> {
                if (a2aResult.result().startsWith("A2A-Fehler:")) {
                    return ToolResult.error(a2aResult.result());
                }
                return ToolResult.success(a2aResult.result());
            })
            .exceptionally(e -> ToolResult.error("Error in sub-agent: " + e.getMessage()));
    }

    public record Params(String prompt) {}

    String getToolName() {
        return toolName;
    }

    String getDescription() {
        return description;
    }
}
