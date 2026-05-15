package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentResult;
import dev.langchain4j.agent.tool.Tool;

public class AgentTool {

    public static final int MAX_RECURSION_DEPTH = 5;
    private static final ScopedValue<Integer> RECURSION_DEPTH = ScopedValue.newInstance();

    private final Agent subAgent;
    private final String toolName;
    private final String description;

    public AgentTool(Agent subAgent, String toolName, String description) {
        this.subAgent = subAgent;
        this.toolName = toolName;
        this.description = description;
    }

    public AgentTool(Agent subAgent, String toolName) {
        this(subAgent, toolName, "Führt einen Sub-Agenten aus: " + toolName);
    }

    @Tool("Führt einen spezialisierten Sub-Agenten aus")
    public String execute(String prompt) {
        int currentDepth = RECURSION_DEPTH.isBound() ? RECURSION_DEPTH.get() : 0;
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return "Fehler: Maximale Rekursionstiefe von " + MAX_RECURSION_DEPTH + " erreicht.";
        }
        try {
            AgentResult result = ScopedValue.where(RECURSION_DEPTH, currentDepth + 1)
                .call(() -> subAgent.execute(prompt));
            return result.finalAnswer();
        } catch (Exception e) {
            return "Fehler im Sub-Agenten: " + e.getMessage();
        }
    }

    String getToolName() {
        return toolName;
    }

    String getDescription() {
        return description;
    }
}
