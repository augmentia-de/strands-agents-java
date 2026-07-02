package de.augmentia.strandsagents.core.subagent;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.interceptor.resilience.Retry;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SubAgentExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final long timeoutSeconds;
    private final int maxRetries;
    private final Map<String, String> metadata;

    public SubAgentExecutor() {
        this(60, 1, Map.of());
    }

    public SubAgentExecutor(long timeoutSeconds, int maxRetries, Map<String, String> metadata) {
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
        this.metadata = Map.copyOf(metadata);
    }

    public SubAgentResult call(Agent agent, String prompt) {
        var agentName = agent.getClass().getSimpleName();
        return call(agent, prompt, agentName);
    }

    public SubAgentResult call(Agent agent, String prompt, String agentName) {
        return call(agent, prompt, agentName, null);
    }

    public SubAgentResult call(Agent agent, String prompt, String agentName, String sessionId) {
        return runCall(agent, prompt, agentName, sessionId);
    }

    private SubAgentResult runCall(Agent agent, String prompt, String agentName, String sessionId) {
        var start = System.nanoTime();
        try {
            var mergedMetadata = metadata;

            Callable<AgentResult> agentCall = () -> {
                var future = CompletableFuture.supplyAsync(
                    () -> sessionId != null ? agent.execute(sessionId, prompt) : agent.execute(prompt),
                    VIRTUAL_EXECUTOR);
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
                return new SubAgentResult(agentName, prompt,
                    "A2A-Fehler: " + e.getMessage(),
                    durationMs, mergedMetadata);
            }

            var durationMs = (System.nanoTime() - start) / 1_000_000;
            return new SubAgentResult(agentName, prompt, result.finalAnswer(),
                durationMs, mergedMetadata);
        } catch (Exception e) {
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            return new SubAgentResult(agentName, prompt,
                "A2A-Fehler: " + e.getMessage(),
                durationMs, metadata);
        }
    }

    public CompletableFuture<SubAgentResult> callAsync(Agent agent, String prompt) {
        return callAsync(agent, prompt, agent.getClass().getSimpleName());
    }

    public CompletableFuture<SubAgentResult> callAsync(Agent agent, String prompt, String agentName) {
        return callAsync(agent, prompt, agentName, null);
    }

    public CompletableFuture<SubAgentResult> callAsync(Agent agent, String prompt, String agentName, String sessionId) {
        var p = prompt;
        var a = agent;
        var n = agentName;
        var s = sessionId;
        return CompletableFuture.supplyAsync(() -> call(a, p, n, s), VIRTUAL_EXECUTOR);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
