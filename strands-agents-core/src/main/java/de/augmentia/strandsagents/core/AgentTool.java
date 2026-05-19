package de.augmentia.strandsagents.core;

import dev.langchain4j.agent.tool.Tool;

public class AgentTool {

    public static final int MAX_RECURSION_DEPTH = 5;
    private static final ScopedValue<Integer> RECURSION_DEPTH = ScopedValue.newInstance();

    private final Agent subAgent;
    private final String toolName;
    private final String description;
    private final A2AExecutor executor;

    public AgentTool(Agent subAgent, String toolName, String description) {
        this(subAgent, toolName, description, new A2AExecutor());
    }

    public AgentTool(Agent subAgent, String toolName, String description, A2AExecutor executor) {
        this.subAgent = subAgent;
        this.toolName = toolName;
        this.description = description;
        this.executor = executor;
    }

    public AgentTool(Agent subAgent, String toolName) {
        this(subAgent, toolName, "Führt einen spezialisierten Sub-Agenten aus: " + toolName);
    }

    public AgentTool(Agent subAgent, String toolName, A2AExecutor executor) {
        this(subAgent, toolName, "Führt einen spezialisierten Sub-Agenten aus: " + toolName, executor);
    }

    @Tool("Führt einen spezialisierten Sub-Agenten aus")
    public String execute(String prompt) {
        int currentDepth = RECURSION_DEPTH.isBound() ? RECURSION_DEPTH.get() : 0;
        if (currentDepth >= MAX_RECURSION_DEPTH) {
            return "Fehler: Maximale Rekursionstiefe von " + MAX_RECURSION_DEPTH + " erreicht.";
        }
        try {
            A2AResult a2aResult = ScopedValue.where(RECURSION_DEPTH, currentDepth + 1)
                .call(() -> executor.call(subAgent, prompt, toolName));
            return a2aResult.result();
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
