package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public record SkillSearchTool(
    Map<String, Skill> skills,
    AgentSkillsPlugin plugin,
    String skillsDirHint
) implements AgentTool<SkillSearchTool.Params> {

    public record Params(String query, String skillName) {
        public Params {
            if (query != null && skillName != null) {
                throw new IllegalArgumentException("Only one of query/skillName may be set");
            }
        }
    }

    @Override
    public String name() {
        return "skill_search";
    }

    @Override
    public String description() {
        return "Search available skills and activate them. Without arguments, lists all skills with descriptions. "
            + "Use 'query' to filter by keyword, or 'skillName' to load full instructions for a specific skill.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public JsonNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();

        var queryProp = factory.objectNode();
        queryProp.put("type", "string");
        queryProp.put("description", "Optional keyword to filter skills by name or description");
        props.set("query", queryProp);

        var nameProp = factory.objectNode();
        nameProp.put("type", "string");
        nameProp.put("description", "Name of a skill to activate and load its full instructions");
        props.set("skillName", nameProp);

        schema.set("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        if (params.skillName() != null) {
            return activateSkill(params.skillName());
        }
        return listSkills(params.query());
    }

    private ToolResult activateSkill(String skillName) {
        var skill = skills.get(skillName);
        if (skill == null) {
            var available = skills.keySet().stream().sorted().collect(Collectors.joining(", "));
            return ToolResult.success("Skill '" + skillName + "' not found.\nAvailable skills: " + available);
        }

        if (plugin != null) {
            return ToolResult.success(plugin.activateSkill(skillName));
        }

        var sb = new StringBuilder();
        sb.append("# Skill activated: ").append(skill.name()).append("\n\n");
        sb.append(skill.instructions()).append("\n");
        if (skill.allowedTools() != null && !skill.allowedTools().isEmpty()) {
            sb.append("\nAllowed tools: ").append(String.join(", ", skill.allowedTools())).append("\n");
        }
        if (skill.path() != null) {
            sb.append("Location: ").append(skill.path()).append("\n");
        }
        return ToolResult.success(sb.toString());
    }

    private ToolResult listSkills(String query) {
        var matching = query != null
            ? skills.values().stream()
                .filter(s -> s.name().toLowerCase().contains(query.toLowerCase())
                    || s.description().toLowerCase().contains(query.toLowerCase()))
                .toList()
            : List.copyOf(skills.values());

        var sb = new StringBuilder();
        if (matching.isEmpty()) {
            sb.append("No skills found");
            if (query != null) sb.append(" for query '").append(query).append("'");
            if (skillsDirHint != null) sb.append(" in ").append(skillsDirHint);
            sb.append(".");
        } else {
            sb.append("Available skills:\n");
            for (var s : matching) {
                sb.append("- ").append(s.name()).append(": ").append(s.description()).append("\n");
            }
            sb.append("\nUse skill_search with skillName to activate a skill.");
        }
        return ToolResult.success(sb.toString());
    }
}
