package de.augmentia.strandsagents.core.tools;

import de.augmentia.strandsagents.core.ToolRegistry.ToolMethod;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

public record McpToolMethod(McpClient client, String serverUrl, String originalToolName, ToolSpecification prefixedSpec)
        implements ToolMethod {

    @Override
    public ToolSpecification spec() {
        return prefixedSpec;
    }

    @Override
    public String execute(String jsonArguments) throws Exception {
        var request = ToolExecutionRequest.builder()
            .id(originalToolName + "-" + System.nanoTime())
            .name(originalToolName)
            .arguments(jsonArguments)
            .build();
        var result = client.executeTool(request);
        return result.resultText();
    }
}
