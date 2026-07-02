package de.augmentia.strandsagents.test;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Test-only decorator that randomly injects failures into tool execution.
 * Controlled via probabilities — not for production use.
 */
public class ChaosTestToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final double timeoutProbability;
    private final double exceptionProbability;
    private final double invalidJsonProbability;
    private final Random random = new Random();

    public ChaosTestToolExecutor(ToolExecutor delegate,
                                  double timeoutProbability,
                                  double exceptionProbability,
                                  double invalidJsonProbability) {
        this.delegate = delegate;
        this.timeoutProbability = timeoutProbability;
        this.exceptionProbability = exceptionProbability;
        this.invalidJsonProbability = invalidJsonProbability;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry) throws Exception {
        double r = random.nextDouble();
        if (r < timeoutProbability) {
            Thread.sleep(5000L);
            throw new RuntimeException("Simulated timeout for tool: " + request.name());
        } else if (r < timeoutProbability + exceptionProbability) {
            throw new RuntimeException("Simulated random error during tool execution: " + request.name());
        } else if (r < timeoutProbability + exceptionProbability + invalidJsonProbability) {
            return new ToolExecutionResult(request.id(), request.name(), "{invalid json", false);
        }
        return delegate.execute(request, registry);
    }

    @Override
    public List<ToolExecutionResult> executeAll(List<ToolExecutionRequest> requests, ToolRegistry registry) throws Exception {
        return delegate.executeAll(requests, registry);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public ToolExecutor withGrantedCapabilities(Set<CapabilityToken> capabilities) {
        delegate.withGrantedCapabilities(capabilities);
        return this;
    }

    @Override
    public Set<CapabilityToken> getGrantedCapabilities() {
        return delegate.getGrantedCapabilities();
    }
}
