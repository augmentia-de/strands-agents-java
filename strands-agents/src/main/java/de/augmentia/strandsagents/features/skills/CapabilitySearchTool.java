package de.augmentia.strandsagents.features.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.ToolResult;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CapabilitySearchTool implements AgentTool<CapabilitySearchTool.Params> {

    private final CapabilityRegistry registry;
    private final ChatModel subAgentModel;
    private final ToolExecutor toolExecutor;
    private final CapabilityEmbeddingService embeddingService;

    public CapabilitySearchTool(CapabilityRegistry registry, ChatModel subAgentModel) {
        this(registry, subAgentModel, new ToolExecutor(), null);
    }

    public CapabilitySearchTool(CapabilityRegistry registry, ChatModel subAgentModel,
                                 ToolExecutor toolExecutor) {
        this(registry, subAgentModel, toolExecutor, null);
    }

    public CapabilitySearchTool(CapabilityRegistry registry, ChatModel subAgentModel,
                                 ToolExecutor toolExecutor,
                                 CapabilityEmbeddingService embeddingService) {
        this.registry = registry;
        this.subAgentModel = subAgentModel;
        this.toolExecutor = toolExecutor;
        this.embeddingService = embeddingService;
    }

    public record Params(String task) {}

    @Override
    public String name() { return "capability_search"; }

    @Override
    public String description() {
        return "Analyze a task and recommend matching capabilities (skills, default tools, and MCP tools). "
            + "Provide a 'task' description for AI-powered analysis across all configured sources. "
            + "Returns structured recommendations.";
    }

    @Override
    public Class<Params> parameterType() { return Params.class; }

    @Override
    public JsonNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();

        var taskProp = factory.objectNode();
        taskProp.put("type", "string");
        taskProp.put("description", "A task description for AI-powered analysis across all capability sources");
        props.set("task", taskProp);

        schema.set("properties", props);
        schema.set("required", factory.arrayNode().add("task"));
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        if (params.task() == null || params.task().isBlank()) {
            return ToolResult.success("Please provide a 'task' describing what you want to do.");
        }

        if (embeddingService != null) {
            var matches = embeddingService.search(params.task());
            if (!matches.isEmpty()) {
                return formatCapabilities(matches);
            }
        }

        return analyzeWithSubAgent(params.task());
    }

    private ToolResult formatCapabilities(List<CapabilityRegistry.Capability> capabilities) {
        var sb = new StringBuilder();
        sb.append("## Vector Search Results\n\n");
        sb.append("**Task:** ").append(capabilities.isEmpty() ? "" : "Found ").append(capabilities.size())
            .append(" matching capabilities:\n\n");
        for (var cap : capabilities) {
            sb.append("- **").append(cap.name()).append("**");
            if (!cap.description().isBlank()) sb.append(": ").append(cap.description());
            sb.append("\n");
        }
        return ToolResult.success(sb.toString());
    }

    private ToolResult analyzeWithSubAgent(String task) {
        var allCapabilities = registry.discoverAll();

        var dirs = registry.skillDirectories();
        var skills = dirs.stream()
            .flatMap(d -> {
                try { return SkillParser.fromDirectory(d).stream(); }
                catch (Exception e) { return java.util.stream.Stream.of(); }
            })
            .collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a));

        var defaultCapabilities = allCapabilities.stream()
            .filter(c -> c.type() == CapabilityRegistry.CapabilityType.DEFAULT)
            .toList();

        var subAgent = new CapabilitySearchAgent(subAgentModel, skills, registry.mcpServers(),
            defaultCapabilities, toolExecutor);
        var result = subAgent.execute(task);

        var sb = new StringBuilder();
        sb.append("## Capability Analysis\n\n");
        sb.append("**Task:** ").append(task).append("\n\n");
        sb.append(result.finalAnswer()).append("\n\n");

        sb.append("### Overview of available capabilities\n\n");
        for (var cap : allCapabilities) {
            sb.append("- [").append(cap.type()).append("] ").append(cap.name());
            sb.append(" (").append(cap.source()).append(")");
            if (!cap.description().isBlank()) sb.append(": ").append(cap.description());
            sb.append("\n");
        }

        return ToolResult.success(sb.toString());
    }
}
