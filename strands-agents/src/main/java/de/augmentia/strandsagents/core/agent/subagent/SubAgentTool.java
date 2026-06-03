package de.augmentia.strandsagents.core.agent.subagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.ToolResult;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SubAgentTool implements AgentTool<SubAgentTool.Params> {

    public static final int MAX_RECURSION_DEPTH = 5;
    private static final ThreadLocal<Integer> RECURSION_DEPTH = new ThreadLocal<>();

    private final Agent subAgent;
    private final String toolName;
    private final String description;
    private final SubAgentExecutor executor;

    public SubAgentTool(Agent subAgent, String toolName, String description) {
        this(subAgent, toolName, description, new SubAgentExecutor());
    }

    public SubAgentTool(Agent subAgent, String toolName, String description, SubAgentExecutor executor) {
        this.subAgent = subAgent;
        this.toolName = toolName;
        this.description = description;
        this.executor = executor;
    }

    public SubAgentTool(Agent subAgent, String toolName) {
        this(subAgent, toolName, "Executes a specialized sub-agent: " + toolName);
    }

    public SubAgentTool(Agent subAgent, String toolName, SubAgentExecutor executor) {
        this(subAgent, toolName, "Executes a specialized sub-agent: " + toolName, executor);
    }

    @Override
    public String name() {
        return toolName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

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

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        Integer prevDepthVal = RECURSION_DEPTH.get();
        int currentDepth = prevDepthVal != null ? prevDepthVal : 0;
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return ToolResult.error("Maximum recursion depth of " + MAX_RECURSION_DEPTH + " reached.");
        }
        var prevDepth = RECURSION_DEPTH.get();
        RECURSION_DEPTH.set(currentDepth + 1);
        try {
            SubAgentResult a2aResult = executor.call(subAgent, params.prompt(), toolName);
            return ToolResult.success(a2aResult.result());
        } catch (Exception e) {
            return ToolResult.error("Error in sub-agent: " + e.getMessage());
        } finally {
            if (prevDepth != null) {
                RECURSION_DEPTH.set(prevDepth);
            } else {
                RECURSION_DEPTH.remove();
            }
        }
    }

    public record Params(String prompt) {}

    String getToolName() {
        return toolName;
    }

    String getDescription() {
        return description;
    }
}
