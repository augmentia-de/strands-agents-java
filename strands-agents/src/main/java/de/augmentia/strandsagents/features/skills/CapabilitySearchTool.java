package de.augmentia.strandsagents.features.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CapabilitySearchTool implements AgentTool<CapabilitySearchTool.Params> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            + "Returns JSON with task, analysis, matchingSkills[] (including declaredTools, resolvedTools, resolveSource, unknownDeclared), matchingTools[], and instruction.";
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
            return jsonResult(params.task(), "Please provide a 'task' describing what you want to do.");
        }

        if (embeddingService != null) {
            var matches = embeddingService.searchTopN(params.task(), 5);
            if (!matches.isEmpty()) {
                return analyzeWithSubAgent(params.task(), matches);
            }
        }

        return analyzeWithSubAgent(params.task());
    }

    private ToolResult analyzeWithSubAgent(String task) {
        return analyzeWithSubAgent(task, null);
    }

    private ToolResult analyzeWithSubAgent(String task, List<CapabilityRegistry.Capability> prefiltered) {
        var allCapabilities = prefiltered != null ? prefiltered : registry.discoverAll();

        Map<String, Skill> skills;
        if (prefiltered != null) {
            var prefNames = prefiltered.stream()
                .filter(c -> c.type() == CapabilityRegistry.CapabilityType.SKILL)
                .map(CapabilityRegistry.Capability::name)
                .collect(Collectors.toSet());
            skills = registry.skillDirectories().stream()
                .flatMap(d -> {
                    try { return SkillParser.fromDirectory(d).stream(); }
                    catch (Exception e) { return java.util.stream.Stream.of(); }
                })
                .filter(s -> prefNames.contains(s.name()))
                .collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a));
        } else {
            skills = registry.skillDirectories().stream()
                .flatMap(d -> {
                    try { return SkillParser.fromDirectory(d).stream(); }
                    catch (Exception e) { return java.util.stream.Stream.of(); }
                })
                .collect(Collectors.toMap(Skill::name, s -> s, (a, b) -> a));
        }

        var defaultCapabilities = allCapabilities.stream()
            .filter(c -> c.type() == CapabilityRegistry.CapabilityType.DEFAULT)
            .toList();

        var jsonInput = MAPPER.createObjectNode();
        jsonInput.put("task", task);
        var prefSkills = jsonInput.putArray("prefilteredSkills");
        for (var s : skills.keySet()) {
            var obj = prefSkills.addObject();
            obj.put("name", s);
            obj.put("description", skills.get(s).description());
        }
        var prefTools = jsonInput.putArray("prefilteredTools");
        for (var cap : defaultCapabilities) {
            var obj = prefTools.addObject();
            obj.put("name", cap.name());
            obj.put("description", cap.description());
        }

        var subAgent = new CapabilitySearchAgent(subAgentModel, skills, registry.mcpServers(),
            defaultCapabilities, toolExecutor);
        var result = subAgent.execute(jsonInput.toString());

        var analysis = result.finalAnswer();
        var recommendedSkills = new ArrayList<String>();
        var recommendedTools = new ArrayList<String>();
        var hasRecommendations = false;
        var toolEnrichments = Map.<String, List<String>>of();

        try {
            var root = MAPPER.readTree(result.finalAnswer());
            if (root.has("analysis")) analysis = root.get("analysis").asText();
            if (root.has("recommendedSkills") && root.get("recommendedSkills").isArray()) {
                for (var s : root.get("recommendedSkills")) recommendedSkills.add(s.asText());
            }
            if (root.has("recommendedTools") && root.get("recommendedTools").isArray()) {
                for (var t : root.get("recommendedTools")) recommendedTools.add(t.asText());
            }
            hasRecommendations = !recommendedSkills.isEmpty() || !recommendedTools.isEmpty();

            if (root.has("toolEnrichments") && root.get("toolEnrichments").isArray()) {
                var enrichMap = new java.util.HashMap<String, List<String>>();
                for (var e : root.get("toolEnrichments")) {
                    var name = e.get("skillName").asText();
                    var tools = new ArrayList<String>();
                    for (var t : e.get("enrichedTools")) tools.add(t.asText());
                    enrichMap.put(name, tools);
                }
                toolEnrichments = enrichMap;
            }
        } catch (Exception ignored) {}

        var knownTools = registry.knownToolNames();

        try {
            var root = MAPPER.createObjectNode();
            root.put("task", task);
            root.put("analysis", analysis);

            var skillsArr = root.putArray("matchingSkills");
            for (var s : skills.values()) {
                if (hasRecommendations && !recommendedSkills.contains(s.name())) continue;
                var obj = skillsArr.addObject();
                obj.put("name", s.name());
                obj.put("description", s.description());
                if (s.allowedTools() != null && !s.allowedTools().isEmpty()) {
                    var toolsArr = obj.putArray("allowedTools");
                    for (var t : s.allowedTools()) toolsArr.add(t);
                }

                var declaredArr = obj.putArray("declaredTools");
                for (var t : s.declaredTools()) declaredArr.add(t);

                var enriched = toolEnrichments.get(s.name());
                if (enriched != null) {
                    var resolvedArr = obj.putArray("resolvedTools");
                    for (var t : enriched) resolvedArr.add(t);
                    obj.put("resolveSource", "llm_enriched");
                } else if (!s.declaredTools().isEmpty()) {
                    var resolvedArr = obj.putArray("resolvedTools");
                    for (var t : s.declaredTools()) resolvedArr.add(t);
                    obj.put("resolveSource", "skill_file");
                }

                var unknownArr = obj.putArray("unknownDeclared");
                for (var t : s.declaredTools()) {
                    if (!knownTools.contains(t)) unknownArr.add(t);
                }
            }

            var toolsArr = root.putArray("matchingTools");
            for (var cap : defaultCapabilities) {
                if (hasRecommendations && !recommendedTools.contains(cap.name())) continue;
                var obj = toolsArr.addObject();
                obj.put("name", cap.name());
                obj.put("description", cap.description());
            }

            root.put("instruction", "Proceed with the matching tools to complete the task. Activate tools via `tool_activator` to get the full skill instructions injected automatically.");
            return new ToolResult(List.of(new TextContent(root.toString())), null);
        } catch (Exception e) {
            var sb = new StringBuilder();
            sb.append("## Capability Analysis\n\n");
            sb.append("**Task:** ").append(task).append("\n\n");
            sb.append(analysis).append("\n\n");
            sb.append("Proceed with the matching tools to complete the task. Activate tools via `tool_activator` to get the full skill instructions injected automatically.\n");
            return ToolResult.success(sb.toString());
        }
    }

    private ToolResult jsonResult(String task, String analysis) {
        try {
            var root = MAPPER.createObjectNode();
            if (task != null) root.put("task", task);
            root.put("analysis", analysis);
            return new ToolResult(List.of(new TextContent(root.toString())), null);
        } catch (Exception e) {
            return ToolResult.success(analysis);
        }
    }
}
