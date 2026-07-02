package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.interceptor.security.CapabilityToken;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Set;

public interface ToolExecutor {

    ToolExecutionResult execute(ToolExecutionRequest request, ToolRegistry registry) throws Exception;

    List<ToolExecutionResult> executeAll(List<ToolExecutionRequest> requests, ToolRegistry registry) throws Exception;

    void shutdown();

    ToolExecutor withGrantedCapabilities(Set<CapabilityToken> capabilities);

    Set<CapabilityToken> getGrantedCapabilities();
}
