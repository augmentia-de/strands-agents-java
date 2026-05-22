package de.augmentia.strandsagents.examples.orchestrator;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.examples.tools.ToolCallingMockModel;
import dev.langchain4j.model.chat.ChatModel;

public class OrchestratorDemo {

    public static void main(String[] args) {
        boolean useMock = false;
        var iterations = -1;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mock" -> useMock = true;
                case "--iterations" -> {
                    if (i + 1 < args.length) iterations = Integer.parseInt(args[++i]);
                }
            }
        }

        if (iterations < 0) iterations = useMock ? 20 : 5;

        ChatModel model = useMock
            ? ToolCallingMockModel.createDefault()
            : ModelFactory.createOpenAiFromEnv();

        var logManager = new LogManager();
        logManager.clear();

        var registry = ToolRegistry.builder()
            .with(new RunTaskTool(model))
            .with(new VerifyAndLogTool(logManager))
            .build();

        var executor = new ToolExecutor(60);
        var resilience = new ResilienceConfig(new RetryConfig(2, 500, 2.0), null);

        var agent = new Agent(model, registry, executor, null, null, resilience);

        // Override MAX_TOOL_ITERATIONS to fit all iterations
        var maxAgentLoop = iterations * 2 + 2;
        try {
            var f1 = de.augmentia.strandsagents.core.agent.Agent.class
                .getDeclaredField("MAX_TOOL_ITERATIONS");
            f1.setAccessible(true);
            f1.setInt(null, maxAgentLoop);

            var f2 = de.augmentia.strandsagents.examples.tools.ToolCallingMockModel.class
                .getDeclaredField("MAX_AGENT_LOOP");
            f2.setAccessible(true);
            f2.setInt(null, maxAgentLoop);
        } catch (Exception e) {
            throw new RuntimeException("Cannot override iteration limit", e);
        }

        boolean abortOnCritical = !useMock;

        var systemPrompt = buildPrompt(iterations, abortOnCritical);

        System.out.println("=== Orchestrator Demo ===");
        System.out.println("  Model: " + (useMock ? "mock" : "openai"));
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Abort on critical: " + abortOnCritical);
        System.out.println();

        var result = agent.execute(systemPrompt);

        var summary = result.finalAnswer();
        logManager.logSummary(summary);

        System.out.println("=== Orchestrator Finished ===");
        System.out.println("  StopReason: " + result.stopReason());
        System.out.println("  Summary: " + truncate(summary, 500));
        System.out.println();
        System.out.println("Log files:");
        System.out.println("  " + logManager.runLog().toAbsolutePath());
        System.out.println("  " + logManager.completeLog().toAbsolutePath());
        System.out.println("  " + logManager.problemsLog().toAbsolutePath());
    }

    private static String buildPrompt(int iterations, boolean abortOnCritical) {
        var abortRef = abortOnCritical
            ? "6. If you see 3+ consecutive runTask errors, still call verifyAndLog to record the failure and continue.\n"
            : "";

        return """
You are a test orchestrator for unreliable tools. Your job is to run %d iterations of varied tasks.

Available tools:
- runTask(taskPrompt) — runs a task against unreliable tools, returns result summary
- verifyAndLog(iteration, taskPrompt, resultSummary) — verifies result and writes logs

The unreliable tools available in each sub-run are:
- add(a, b) — calculator, may return wrong result, throw, or timeout
- getCurrentWeather(city) — weather, may timeout, return empty, or throw
- readFile(path) — reads file from /tmp, may return wrong content, timeout, or throw
- writeFile(path, content) — writes file in /tmp, may silently write elsewhere, timeout, or throw

Instructions:
1. Generate a TASK for each iteration. Tasks should vary significantly:
   - Use 1-3 tools per task (calculator, weather, file read, file write)
   - Combine tools in different ways (e.g. "calculate X + Y and write to /tmp/r.txt")
   - Vary cities, numbers, and file paths between iterations
2. Call runTask with your task prompt
3. Call verifyAndLog with the iteration number, your task, and exactly what runTask returned
4. If runTask returns an error, still call verifyAndLog to record the failure
5. Repeat until iteration %d is complete
6. After the last iteration, provide a summary of all results including:
   - How many tasks succeeded vs had problems
   - Common failure patterns observed
   - Tool call statistics
%s
Do NOT stop early. Complete all %d iterations.
"""
            .formatted(iterations, iterations - 1, abortRef, iterations);
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
