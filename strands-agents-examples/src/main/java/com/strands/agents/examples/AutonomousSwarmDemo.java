package com.strands.agents.examples;

import com.strands.agents.core.AgentTool;
import com.strands.agents.core.ModelFactory;
import com.strands.agents.core.StrandsAgent;

/**
 * Autonomous Swarm Demo (Java).
 * 
 * This demo showcases "Swarm Intelligence" where agents collaborate autonomously 
 * using handoffs via AgentTools, mirroring the Python swarm.py functionality.
 * 
 * Key concepts:
 * 1. Specialized Agents: Each agent has a specific role and system prompt.
 * 2. Agent-as-a-Tool: Agents are registered as tools within other agents.
 * 3. Autonomous Handoffs: The LLM decides when to call another agent based on the task.
 */
public class AutonomousSwarmDemo {

    public static void main(String[] args) {
        System.out.println("🐝 Starting Autonomous Swarm Demo (Java)");
        
        AutonomousSwarmDemo demo = new AutonomousSwarmDemo();
        demo.runSwarmCollaboration();
    }

    public void runSwarmCollaboration() {
        // 1. Create specialized agents
        // In a real scenario, these could use different models (e.g., GPT-4 for lead, Claude for analysis)
        StrandsAgent researcher = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        researcher.setSystemPrompt("You are an expert Market Researcher. Your objective is to extract, " +
                "synthesize, and summarize critical market trends, competitive intelligence, and consumer insights. " +
                "Focus on providing high-signal data. If you encounter financial data requiring deep ROI, " +
                "market share calculations, or complex modeling, delegate the task to the 'finance_expert' tool. " +
                "Always conclude with a concise, executive summary of your findings.");

        StrandsAgent financeExpert = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        financeExpert.setSystemPrompt("You are a Senior Financial Analyst. Your expertise covers " +
                "quantitative modeling, ROI projections, and market share assessment. " +
                "Analyze the qualitative data provided to you and transform it into precise " +
                "numerical insights and financial forecasts. Focus on accuracy and statistical significance.");

        // 2. Enable Handoffs
        // Register the financeExpert as a tool for the researcher.
        // This allows the researcher to "hand off" work to the expert autonomously.
        researcher.getToolRegistry().register(new AgentTool(
            financeExpert, 
            "finance_expert", 
            "Use this tool to delegate complex financial calculations or ROI analysis to an expert."
        ));

        // 3. Execute the swarm
        // We start with the researcher. The researcher will decide on its own 
        // whether to call the finance_expert based on the prompt.
        String task = "Analyze the potential of the European EV market in 2024. " +
                     "I need both general trends and a specific ROI estimation for a new charging station network.";
        
        System.out.println("\n--- Task: " + task + " ---");
        System.out.println("Requesting researcher to start...");

        var result = researcher.execute(task);

        // 4. Print results
        System.out.println("\n--- FINAL CONSOLIDATED RESULT ---");
        System.out.println(result.finalAnswer());
        
        System.out.println("\n--- EXECUTION METRICS ---");
        System.out.println("Session ID: " + result.sessionId());
        System.out.println("Stop Reason: " + result.stopReason());
    }
}
