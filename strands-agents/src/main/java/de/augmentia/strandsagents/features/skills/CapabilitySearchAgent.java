package de.augmentia.strandsagents.features.skills;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;

public class CapabilitySearchAgent extends Agent {

    public record ToolEnrichment(
        String skillName,
        List<String> enrichedTools
    ) {}

    public record Analysis(
        String analysis,
        List<String> recommendedSkills,
        List<String> recommendedTools,
        String reasoning,
        List<ToolEnrichment> toolEnrichments
    ) {}

    public CapabilitySearchAgent(ChatModel model, Map<String, Skill> skills,
                                  List<CapabilityRegistry.McpServerConfig> mcpServers,
                                  List<CapabilityRegistry.Capability> defaultCapabilities,
                                  ToolExecutor toolExecutor) {
        super(model, new ToolRegistry(), toolExecutor);
        setSystemPrompt(buildPrompt(skills, mcpServers, defaultCapabilities));
    }

    private static String buildPrompt(Map<String, Skill> skills,
                                       List<CapabilityRegistry.McpServerConfig> mcpServers,
                                       List<CapabilityRegistry.Capability> defaultCapabilities) {
        var sb = new StringBuilder();
        sb.append("""
            You are a capability analysis agent. Your task is to find the best-matching skills and tools for a given task.

            The user input will be a JSON object with:
            - "task": the user's task description
            - "prefilteredSkills": array of {"name": "...", "description": "..."} for skills that may be relevant
            - "prefilteredTools": array of {"name": "...", "description": "..."} for default tools that may be relevant

            Below is the full list of available capabilities. Analyze the task against them and recommend the best matches.

            RULES:
            1. When both a named skill and a default tool match the same need, PREFER the skill.
            2. Group results by functional area.
            3. CLEARLY separate skills from bare tools in your output.
            4. If the user's task does not match any capability, state that clearly.

            TOOL ENRICHMENT:
            Each skill may declare a list of tools it uses via `declaredTools`. Review these for each recommended skill:
            - If the skill is missing essential standard tools, add them to `enrichedTools` in `toolEnrichments`
            - If a declared tool appears to be a typo (e.g. "wite" instead of "write"), correct it
            - If a declared tool implies a related tool (e.g. "find" implies "read"), add the implied tool
            - Explain your reasoning for each enrichment in `analysis`
            - If no enrichment is needed, set `toolEnrichments` to an empty list

            """);

        if (!skills.isEmpty()) {
            sb.append("## Available Skills\n\n");
            for (var s : skills.values()) {
                sb.append("### ").append(s.name()).append("\n\n");
                sb.append("**Description:** ").append(s.description()).append("\n\n");
                sb.append("**Instructions:**\n\n");
                try {
                    var skillMd = SkillParser.findSkillMdFile(s.path());
                    sb.append(java.nio.file.Files.readString(skillMd)).append("\n\n");
                } catch (Exception e) {
                    sb.append(s.instructions()).append("\n\n");
                }
                if (s.allowedTools() != null && !s.allowedTools().isEmpty()) {
                    sb.append("**Allowed tools:** ").append(String.join(", ", s.allowedTools())).append("\n\n");
                }
                if (!s.declaredTools().isEmpty()) {
                    sb.append("**Declared tools:** ").append(String.join(", ", s.declaredTools())).append("\n\n");
                }
            }
        }

        if (mcpServers != null && !mcpServers.isEmpty()) {
            sb.append("## Configured MCP Servers\n\n");
            for (var s : mcpServers) {
                sb.append("- ").append(s.name());
                sb.append(" (").append(s.url()).append(")\n");
            }
            sb.append("\n");
        }

        if (defaultCapabilities != null && !defaultCapabilities.isEmpty()) {
            sb.append("## Available Default Tools\n\n");
            for (var cap : defaultCapabilities) {
                sb.append("- ").append(cap.name());
                if (!cap.description().isBlank()) sb.append(": ").append(cap.description());
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("""
            Analyze the user's task against the capabilities listed above.
            Recommend the best-matching skills, default tools, and MCP tools.
            Skills should always be preferred over bare tools when they cover the same functionality.

            Respond with valid JSON in this exact structure:
            {
              "analysis": "string",
              "recommendedSkills": ["string"],
              "recommendedTools": ["string"],
              "reasoning": "string",
              "toolEnrichments": [
                {"skillName": "string", "enrichedTools": ["string"]}
              ]
            }
            """);

        return sb.toString();
    }
}
