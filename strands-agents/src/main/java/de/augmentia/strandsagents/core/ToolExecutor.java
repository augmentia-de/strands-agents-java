package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Set;

/**
 * Executes tool requests against a ToolRegistry with capability-based access control.
 */
public interface ToolExecutor {

    /**
     * Executes a single tool request and returns the result.
     *
     * @param request the tool execution request from the LLM
     * @param registry the registry to look up the tool
     */
    ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry) throws Exception;

    /**
     * Executes multiple tool requests concurrently and returns all results.
     */
    List<ToolExecutionResult> executeAll(List<ToolExecutionRequest> requests, ToolRegistry registry) throws Exception;

    /**
     * Shuts down the executor, releasing any held resources.
     */
    void shutdown();

    /**
     * Returns this executor configured with the given granted capabilities for access control.
     */
    ToolExecutor withGrantedCapabilities(Set<CapabilityToken> capabilities);

    Set<CapabilityToken> getGrantedCapabilities();
}
