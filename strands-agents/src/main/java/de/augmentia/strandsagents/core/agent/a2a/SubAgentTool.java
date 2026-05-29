package de.augmentia.strandsagents.core.agent.a2a;

import de.augmentia.strandsagents.core.agent.Agent;
import dev.langchain4j.agent.tool.Tool;

public class SubAgentTool {

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
        this(subAgent, toolName, "Führt einen spezialisierten Sub-Agenten aus: " + toolName);
    }

    public SubAgentTool(Agent subAgent, String toolName, SubAgentExecutor executor) {
        this(subAgent, toolName, "Führt einen spezialisierten Sub-Agenten aus: " + toolName, executor);
    }

    @Tool("Führt einen spezialisierten Sub-Agenten aus")
    public String execute(String prompt) {
        Integer prevDepthVal = RECURSION_DEPTH.get();
        int currentDepth = prevDepthVal != null ? prevDepthVal : 0;
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return "Fehler: Maximale Rekursionstiefe von " + MAX_RECURSION_DEPTH + " erreicht.";
        }
        var prevDepth = RECURSION_DEPTH.get();
        RECURSION_DEPTH.set(currentDepth + 1);
        try {
            SubAgentResult a2aResult = executor.call(subAgent, prompt, toolName);
            return a2aResult.result();
        } catch (Exception e) {
            return "Fehler im Sub-Agenten: " + e.getMessage();
        } finally {
            if (prevDepth != null) {
                RECURSION_DEPTH.set(prevDepth);
            } else {
                RECURSION_DEPTH.remove();
            }
        }
    }

    String getToolName() {
        return toolName;
    }

    String getDescription() {
        return description;
    }
}
