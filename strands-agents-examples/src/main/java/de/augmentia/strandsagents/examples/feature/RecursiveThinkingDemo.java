package de.augmentia.strandsagents.examples.feature;


import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.subagent.SubAgentExecutor;
import de.augmentia.strandsagents.features.subagent.SubAgentResult;
import de.augmentia.strandsagents.config.ModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive Thinking Demo (Java).
 * 
 * This example implements the functionality of the Python 'think.py' tool.
 * It allows an agent to process a complex thought through multiple iterative cycles,
 * with each cycle building upon the insights of the previous one.
 * 
 * Key Features:
 * 1. Iterative Cycles: Deepens the analysis in each step.
 * 2. Specialized Persona: Uses a dedicated agent configuration for thinking.
 * 3. Result Chaining: Automatically passes insights from Cycle N to Cycle N+1.
 */
public class RecursiveThinkingDemo {

    public static void main(String[] args) {
        System.out.println("🧠 Starting Recursive Thinking Demo (Java)");
        
        RecursiveThinkingDemo demo = new RecursiveThinkingDemo();
        
        String topic = "The long-term impact of sustainable energy on global geopolitics.";
        int numberOfCycles = 3;
        
        demo.runThinkingProcess(topic, numberOfCycles);
    }

    /**
     * Executes the recursive thinking process.
     * 
     * @param initialThought The topic to think about.
     * @param cycles How many iterations of thinking to perform.
     */
    public void runThinkingProcess(String initialThought, int cycles) {
        // 1. Setup the "Thinking Agent"
        // In a real world scenario, you might use a more powerful model for thinking
        // (e.g., switching from GPT-4o-mini to GPT-4o for the actual thinking cycles).
        Agent thinkingAgent = new Agent(ModelFactory.createOpenAiFromEnv());
        thinkingAgent.setSystemPrompt("You are an Elite Analytical Strategist. Your goal is to subject an initial " +
                "concept to rigorous recursive analysis. In each cycle, you must:\n" +
                "1. **Analyze:** Evaluate the current thought from multiple perspectives (economic, social, technical).\n" +
                "2. **Extrapolate:** Explore second and third-order effects that are not immediately obvious.\n" +
                "3. **Synthesize:** Build upon the previous cycle's insights to reach a higher level of abstraction and strategic depth.\n\n" +
                "Avoid repetition; prioritize the discovery of new implications and potential counter-arguments.");

        // 2. Use A2AExecutor to manage the execution
        SubAgentExecutor executor = new SubAgentExecutor();
        
        String currentInsight = initialThought;
        List<String> history = new ArrayList<>();

        System.out.println("\nInitial Thought: " + initialThought);

        // 3. Recursive Loop
        for (int i = 1; i <= cycles; i++) {
            System.out.println("\n--- 🧠 Thinking Cycle " + i + "/" + cycles + " ---");
            
            // Build the prompt for the current cycle
            String prompt = String.format(
                "Based on your previous analysis: '%s'\n\nPlease take this further. " +
                "Identify deeper implications or potential counter-arguments.", 
                currentInsight
            );

            // Execute thinking cycle
            SubAgentResult result = executor.call(thinkingAgent, prompt);
            
            // Store and update for next iteration
            currentInsight = result.result();
            history.add(currentInsight);
            
            System.out.println("New Insights generated.");
        }

        // 4. Final Output
        System.out.println("\n==========================================");
        System.out.println("🏁 FINAL ANALYTICAL CONCLUSION");
        System.out.println("==========================================");
        System.out.println(currentInsight);
        System.out.println("==========================================");
    }
}
