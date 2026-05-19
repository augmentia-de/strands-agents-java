package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.StrandsAgent;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.ToolResult;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CapabilitySearchTool implements AgentTool<CapabilitySearchTool.Params> {

    private final CapabilityRegistry registry;
    private final ChatModel subAgentModel;

    public CapabilitySearchTool(CapabilityRegistry registry, ChatModel subAgentModel) {
        this.registry = registry;
        this.subAgentModel = subAgentModel;
    }

    public record Params(String task, String query) {}

    @Override
    public String name() { return "capability_search"; }

    @Override
    public String description() {
        return "Search for capabilities (skills and MCP tools) relevant to a task. "
            + "Provide a 'task' description for AI-powered analysis across all configured sources, "
            + "or a 'query' for simple keyword filtering. "
            + "Returns structured recommendations with skills and MCP tools matching your needs.";
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

        var queryProp = factory.objectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Optional keyword filter when no task is provided");
        props.set("query", queryProp);

        schema.set("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        if (params.task() != null && !params.task().isBlank()) {
            return analyzeWithSubAgent(params.task());
        }
        var capabilities = params.query() != null
            ? registry.search(params.query())
            : registry.discoverAll();
        return formatCapabilities(capabilities);
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

        var subRegistry = new ToolRegistry();
        subRegistry.register(new SkillSearchTool(skills, null, "all configured directories"));
        if (!registry.mcpServers().isEmpty()) {
            subRegistry.register(new McpListTool(registry.mcpServers()));
        }

        var systemPrompt = """
            You are a capability analysis agent. Find relevant skills and MCP tools for a given task.

            Steps:
            1. Call skill_search to list all available skills
            2. If MCP servers are configured, call mcp_list to see their tools
            3. For any promising skill, call skill_search(skillName="...") to inspect its full instructions
            4. Analyze the task and select the most relevant capabilities
            5. Remove duplicates and similar tools

            Return a JSON object with:
            {
              "reasoning": "brief explanation",
              "recommendedSkills": ["skill1", "skill2"],
              "recommendedMcpTools": ["tool1", "tool2"],
              "summary": "human-readable summary"
            }
            """;

        var subAgent = new StrandsAgent(subAgentModel, subRegistry, new ToolExecutor());
        subAgent.setSystemPrompt(systemPrompt);
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

    private ToolResult formatCapabilities(List<CapabilityRegistry.Capability> capabilities) {
        var sb = new StringBuilder();
        if (capabilities.isEmpty()) {
            sb.append("No capabilities found.");
        } else {
            sb.append("Capabilities (").append(capabilities.size()).append("):\n\n");
            for (var cap : capabilities) {
                sb.append("- [").append(cap.type()).append("] ").append(cap.name());
                sb.append(" (").append(cap.source()).append(")");
                if (!cap.description().isBlank()) sb.append(": ").append(cap.description());
                sb.append("\n");
            }
        }
        return ToolResult.success(sb.toString());
    }
}
