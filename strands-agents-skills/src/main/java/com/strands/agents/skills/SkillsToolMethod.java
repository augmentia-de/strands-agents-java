package com.strands.agents.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strands.agents.core.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.*;

public record SkillsToolMethod(AgentSkillsPlugin plugin) implements ToolRegistry.ToolMethod {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolSpecification spec() {
        return ToolSpecification.builder()
            .name("skills")
            .description("Activate a skill to load its full instructions. " +
                "Use this tool to load the complete instructions for a skill listed in " +
                "the available_skills section of your system prompt.")
            .parameters(JsonObjectSchema.builder()
                .addProperty("skill_name", JsonStringSchema.builder().build())
                .build())
            .build();
    }

    @Override
    public String execute(String jsonArguments) throws Exception {
        var args = MAPPER.readValue(jsonArguments,
            new TypeReference<Map<String, String>>() {});
        var skillName = args.get("skill_name");
        if (skillName == null || skillName.isBlank())
            return "Error: 'skill_name' parameter is required.";

        return plugin.activateSkill(skillName);
    }
}
