package com.strands.agents.examples;

import com.strands.agents.core.A2AExecutor;
import com.strands.agents.core.A2AResult;
import com.strands.agents.core.ModelFactory;
import com.strands.agents.core.StrandsAgent;
import com.strands.agents.core.ToolRegistry;
import com.strands.agents.core.ToolExecutor;
import com.strands.agents.core.model.agent.AgentResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Demo showing how to implement complex workflows in Java using Strands.
 * This mirrors the functionality of the Python workflow tool by using:
 * - A2AExecutor for parallel execution and retries
 * - CompletableFuture for dependency management
 * - Specialized StrandsAgent instances for different tasks/models
 */
public class WorkflowDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Advanced Java Workflow Demo");
        
        WorkflowDemo demo = new WorkflowDemo();
        demo.runParallelResearchWorkflow();
    }

    public void runParallelResearchWorkflow() {
        // 1. Setup shared components
        // A2AExecutor uses Virtual Threads and handles timeouts/retries
        A2AExecutor executor = new A2AExecutor(120, 2, java.util.Map.of("workflow", "research-2024"));

        // 2. Create specialized agents for different tasks (could use different models)
        StrandsAgent researchAgent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        StrandsAgent analystAgent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        StrandsAgent writerAgent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());

        System.out.println("📋 Phase 1: Parallel Data Collection");

        // Start multiple tasks in parallel
        CompletableFuture<A2AResult> dataTask1 = executor.callAsync(researchAgent, 
            "Analyze current trends in Solar Energy for 2024. Provide 3 key points.");
        
        CompletableFuture<A2AResult> dataTask2 = executor.callAsync(researchAgent, 
            "Analyze current trends in Wind Energy for 2024. Provide 3 key points.");

        // 3. Dependency Management: Analysis starts when BOTH collection tasks are done
        CompletableFuture<A2AResult> analysisTask = CompletableFuture.allOf(dataTask1, dataTask2)
            .thenCompose(v -> {
                System.out.println("📊 Phase 2: Cross-Sector Analysis");
                
                String combinedData = String.format("Solar Trends: %s\n\nWind Trends: %s", 
                    dataTask1.join().result(), dataTask2.join().result());
                
                String prompt = "Based on the following data, identify common challenges for both sectors:\n" + combinedData;
                return executor.callAsync(analystAgent, prompt);
            });

        // 4. Final Step: Reporting
        CompletableFuture<A2AResult> reportTask = analysisTask.thenCompose(analysisResult -> {
            System.out.println("📝 Phase 3: Final Report Generation");
            
            String prompt = "Create a summary report for an executive board based on this analysis: " + analysisResult.result();
            return executor.callAsync(writerAgent, prompt);
        });

        // 5. Handle results and errors
        reportTask.handle((result, ex) -> {
            if (ex != null) {
                System.err.println("❌ Workflow failed: " + ex.getMessage());
            } else {
                System.out.println("\n--- FINAL WORKFLOW REPORT ---");
                System.out.println(result.result());
                System.out.println("-----------------------------");
                System.out.println("⏱️ Total Time: " + result.durationMs() + "ms");
            }
            return null;
        }).join();
    }

    /**
     * Demonstrates a more dynamic approach with a list of tasks (similar to the Python list of dicts).
     */
    public void runDynamicBatchWorkflow(List<String> topics) {
        A2AExecutor executor = new A2AExecutor();
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());

        System.out.println("📦 Processing batch of " + topics.size() + " topics in parallel...");

        List<CompletableFuture<A2AResult>> futures = topics.stream()
            .map(topic -> executor.callAsync(agent, "Summarize the significance of " + topic))
            .collect(Collectors.toList());

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> {
                System.out.println("✅ Batch processing complete.");
                futures.forEach(f -> System.out.println("- " + f.join().agentName() + " finished a task."));
            }).join();
    }
}
