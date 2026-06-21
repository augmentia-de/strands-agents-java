package de.augmentia.strandsagents.features.skills;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;

public class CapabilitySearchAgent extends Agent {

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

            Below is the full list of available capabilities. Analyze the task and recommend the best matches.

            RULES:
            1. When both a named skill and a default tool match the same need, PREFER the skill.
            2. Group results by functional area.
            3. CLEARLY separate skills from bare tools in your output.
            4. If the user's task does not match any capability, state that clearly.

            OUTPUT FORMAT:
            ## Matching Skills
            - [skill name] (tool: tool_name): description

            ## Matching Default Tools
            - [tool name]: description

            """);

        if (!skills.isEmpty()) {
            sb.append("## Available Skills\n\n");
            for (var s : skills.values()) {
                sb.append("- ").append(s.name());
                sb.append(": ").append(s.description());
                if (s.allowedTools() != null && !s.allowedTools().isEmpty()) {
                    sb.append(" (uses tool: ").append(String.join(", ", s.allowedTools())).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
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
            """);

        return sb.toString();
    }
}
