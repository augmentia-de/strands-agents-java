package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.core.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Tool that lists all registered tools with their names and descriptions.
 */
public class ListToolsTool implements AgentTool<ListToolsTool.Params> {

    private static final Logger log = LoggerFactory.getLogger(ListToolsTool.class);
    private final ToolRegistry registry;

    public ListToolsTool(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "list_tools";
    }

    @Override
    public String description() {
        return "Lists all available tools with their names and descriptions. Use this to discover what tools are at your disposal.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    /** Returns an empty JSON schema (no parameters needed). */
    @Override
    public ObjectNode parameterSchema() {
        return JsonNodeFactory.instance.objectNode();
    }

    /** Returns a formatted list of all registered tools excluding list_tools itself. */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        var lines = registry.getSpecifications().stream()
            .filter(spec -> !spec.name().equals("list_tools"))
            .sorted(Comparator.comparing(spec -> spec.name()))
            .map(spec -> {
                var desc = spec.description();
                if (desc == null || desc.isBlank()) {
                    return "- " + spec.name();
                }
                return "- " + spec.name() + ": " + desc;
            })
            .collect(Collectors.joining("\n"));

        var summary = (registry.size() - 1) + " tool(s) registered:\n" + lines;
        return ToolResult.success(summary);
    }

    /** Empty params record (no arguments needed). */
    public record Params() {}
}
