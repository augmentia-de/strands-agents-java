package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.features.security.CapabilityToken;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;

public class ToolExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();
    private static final Random RANDOM = new Random();

    private final long timeoutSeconds;
    private final boolean randomFailureEnabled;
    private final double timeoutProbability;
    private final double exceptionProbability;
    private final double invalidJsonProbability;
    private Set<CapabilityToken> grantedCapabilities = Set.of();

    public ToolExecutor() {
        this(Long.getLong("loop.tool-executor.timeout-seconds",
            Integer.getInteger("loop.tool-executor.timeout-seconds", 300)));
    }

    public ToolExecutor(long timeoutSeconds) {
        this(timeoutSeconds,
            Boolean.parseBoolean(System.getenv("RANDOM_TOOL_ERRORS_ENABLED")),
            parseDoubleEnvStatic("RANDOM_TOOL_TIMEOUT_PROBABILITY", 0.1),
            parseDoubleEnvStatic("RANDOM_TOOL_EXCEPTION_PROBABILITY", 0.1),
            parseDoubleEnvStatic("RANDOM_TOOL_INVALID_JSON_PROBABILITY", 0.1));
    }

    public ToolExecutor(long timeoutSeconds,
                        boolean randomFailureEnabled,
                        double timeoutProbability,
                        double exceptionProbability,
                        double invalidJsonProbability) {
        this.timeoutSeconds = timeoutSeconds;
        this.randomFailureEnabled = randomFailureEnabled;
        this.timeoutProbability = timeoutProbability;
        this.exceptionProbability = exceptionProbability;
        this.invalidJsonProbability = invalidJsonProbability;
    }

    public ToolExecutor withGrantedCapabilities(Set<CapabilityToken> capabilities) {
        this.grantedCapabilities = capabilities != null ? capabilities : Set.of();
        return this;
    }

    public Set<CapabilityToken> getGrantedCapabilities() {
        return grantedCapabilities;
    }

    static double parseDoubleEnvStatic(String envVarName, double defaultValue) {
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isBlank()) {
            try {
                return Double.parseDouble(envValue);
            } catch (NumberFormatException e) {
                System.err.println("Warning: Invalid number format for environment variable " + envVarName + ". Using default value " + defaultValue);
            }
        }
        return defaultValue;
    }

    public List<ToolExecutionResult> executeAll(
            List<ToolExecutionRequest> requests,
            ToolRegistry registry) throws Exception {

        var futures = requests.stream()
            .map(req -> VIRTUAL_EXECUTOR.submit(() -> executeSingle(req, registry)))
            .toList();

        var results = new ArrayList<ToolExecutionResult>();
        for (var future : futures) {
            try {
                results.add(future.get(timeoutSeconds, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Tool execution timed out");
            }
        }
        return results;
    }

    public ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {
        return executeSingle(request, registry);
    }

    ToolExecutionResult executeSingle(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {

        if (randomFailureEnabled) {
            double rand = RANDOM.nextDouble(); // 0.0 to 1.0

            if (rand < timeoutProbability) {
                // Simulate timeout
                System.out.println("Simulating timeout for tool: " + request.name());
                Thread.sleep((timeoutSeconds + 1) * 1000); // Sleep longer than timeout
                throw new RuntimeException("Simulated timeout for tool: " + request.name()); // This line will likely not be reached if future.get() catches it
            } else if (rand < timeoutProbability + exceptionProbability) {
                // Simulate exception
                System.out.println("Simulating exception for tool: " + request.name());
                throw new RuntimeException("Simulated random error during tool execution: " + request.name());
            } else if (rand < timeoutProbability + exceptionProbability + invalidJsonProbability) {
                // Simulate invalid JSON result
                System.out.println("Simulating invalid JSON result for tool: " + request.name());
                return new ToolExecutionResult(request.id(), request.name(), "{invalid json", false);
            }
        }

        var toolMethod = registry.get(request.name());

        var requiredCap = toolMethod.requiredCapability();
        if (requiredCap != null && !grantedCapabilities.contains(requiredCap)) {
            throw new SecurityException(
                "Tool '" + request.name() + "' requires capability " + requiredCap
                + " but executor only has: " + grantedCapabilities);
        }

        var future = VIRTUAL_EXECUTOR.submit(() -> toolMethod.execute(request.arguments()));
        String result;
        try {
            result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RuntimeException(
                "Tool '" + request.name() + "' timeout after " + timeoutSeconds + "s");
        }

        return new ToolExecutionResult(
            request.id(), request.name(), result, false);
    }

    public void shutdown() {
        VIRTUAL_EXECUTOR.shutdown();
    }
}
