package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.tools.builtin.ReadTool;
import de.augmentia.strandsagents.tools.builtin.WriteTool;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A tool that activates or deactivates other tools at runtime. */
public class ToolActivator implements AgentTool<ToolActivator.Params> {

    private final ToolRegistry registry;
    private final Map<String, AgentTool<?>> available = new ConcurrentHashMap<>();

    /** Creates a ToolActivator with the given registry and workspace path. */
    public ToolActivator(ToolRegistry registry, Path workspace) {
        this.registry = registry;
        available.put("write", new WriteTool(workspace));
        available.put("read", new ReadTool(workspace));
    }

    public record Params(String action, String tool) {}

    /** Returns the tool name. */
    @Override
    public String name() { return "tool_activator"; }

    /** Returns the tool description. */
    @Override
    public String description() {
        return "Activate or deactivate a tool by name. "
            + "Use action=\"add\" to make a tool available, action=\"remove\" to hide it. "
            + "Available tools: write, read.";
    }

    /** Returns the parameter type class. */
    @Override
    public Class<Params> parameterType() { return Params.class; }

    /** Returns the JSON schema for the tool parameters. */
    @Override
    public JsonNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();

        var actionProp = factory.objectNode();
        actionProp.put("type", "string");
        actionProp.put("description", "Action: \"add\" to activate a tool, \"remove\" to deactivate");
        props.set("action", actionProp);

        var toolProp = factory.objectNode();
        toolProp.put("type", "string");
        toolProp.put("description", "Tool name to activate or deactivate (e.g. \"write\", \"read\")");
        props.set("tool", toolProp);

        schema.set("properties", props);
        var required = schema.putArray("required");
        required.add("action");
        required.add("tool");
        return schema;
    }

    /** Executes the activation or deactivation of a tool. */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        var action = params.action();
        var tool = params.tool();
        if (action == null || tool == null) {
            return ToolResult.error("Both 'action' and 'tool' parameters are required");
        }
        return switch (action) {
            case "add" -> doAdd(tool);
            case "remove" -> doRemove(tool);
            default -> ToolResult.error("Unknown action '" + action + "'. Use 'add' or 'remove'.");
        };
    }

    private ToolResult doAdd(String name) {
        var tool = available.get(name);
        if (tool == null) {
            return ToolResult.error("Unknown tool '" + name + "'. Available: write, read.");
        }
        registry.register(tool);
        return ToolResult.success("Tool '" + name + "' activated. It is now available for use.");
    }

    private ToolResult doRemove(String name) {
        registry.remove(name);
        return ToolResult.success("Tool '" + name + "' deactivated. It is no longer available.");
    }
}
