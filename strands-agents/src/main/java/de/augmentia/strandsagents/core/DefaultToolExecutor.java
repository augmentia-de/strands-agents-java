package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Default ToolExecutor that runs tools in virtual threads with configurable timeout.
 */
public class DefaultToolExecutor implements ToolExecutor {

    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final long timeoutSeconds;
    private Set<CapabilityToken> grantedCapabilities = Set.of();

    public DefaultToolExecutor() {
        this(Long.getLong("loop.tool-executor.timeout-seconds",
            Integer.getInteger("loop.tool-executor.timeout-seconds", 300)));
    }

    public DefaultToolExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ToolExecutor withGrantedCapabilities(Set<CapabilityToken> capabilities) {
        this.grantedCapabilities = capabilities != null ? capabilities : Set.of();
        return this;
    }

    @Override
    public Set<CapabilityToken> getGrantedCapabilities() {
        return grantedCapabilities;
    }

    @Override
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

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {
        return executeSingle(request, registry);
    }

    /**
     * Executes a single request with capability checking and timeout enforcement.
     */
    ToolExecutionResult executeSingle(ToolExecutionRequest request, ToolRegistry registry)
            throws Exception {

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

    @Override
    public void shutdown() {
        VIRTUAL_EXECUTOR.shutdown();
    }
}
