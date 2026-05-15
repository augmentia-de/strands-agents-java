package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.resilience.Retry;
import com.strands.agents.core.resilience.RetryConfig;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class A2AExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final long timeoutSeconds;
    private final int maxRetries;
    private final Map<String, String> metadata;

    public A2AExecutor() {
        this(60, 1, Map.of());
    }

    public A2AExecutor(long timeoutSeconds, int maxRetries, Map<String, String> metadata) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
        this.metadata = Map.copyOf(metadata);
    }

    public A2AResult call(Agent agent, String prompt) {
        var agentName = agent.getClass().getSimpleName();
        return call(agent, prompt, agentName);
    }

    public A2AResult call(Agent agent, String prompt, String agentName) {
        var start = System.nanoTime();
        try {
            var mergedMetadata = metadata;

            Callable<AgentResult> agentCall = () -> {
                var future = CompletableFuture.supplyAsync(() -> agent.execute(prompt), VIRTUAL_EXECUTOR);
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            };

            RetryConfig retryCfg = new RetryConfig(
                maxRetries + 1, 500, 2.0);

            AgentResult result;
            try {
                result = Retry.run(agentCall, retryCfg,
                    e -> !(e instanceof IllegalArgumentException));
            } catch (Exception e) {
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                return new A2AResult(agentName, prompt,
                    "A2A-Fehler: " + e.getMessage(),
                    durationMs, mergedMetadata);
            }

            var durationMs = (System.nanoTime() - start) / 1_000_000;
            return new A2AResult(agentName, prompt, result.finalAnswer(),
                durationMs, mergedMetadata);
        } catch (Exception e) {
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            return new A2AResult(agentName, prompt,
                "A2A-Fehler: " + e.getMessage(),
                durationMs, metadata);
        }
    }

    public CompletableFuture<A2AResult> callAsync(Agent agent, String prompt) {
        return callAsync(agent, prompt, agent.getClass().getSimpleName());
    }

    public CompletableFuture<A2AResult> callAsync(Agent agent, String prompt, String agentName) {
        return CompletableFuture.supplyAsync(() -> call(agent, prompt, agentName), VIRTUAL_EXECUTOR);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
