package com.strands.agents.core.tools;

import com.strands.agents.core.ToolRegistry.ToolMethod;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

public record McpToolMethod(McpClient client, String toolName, ToolSpecification spec)
        implements ToolMethod {

    @Override
    public String execute(String jsonArguments) throws Exception {
        var request = ToolExecutionRequest.builder()
            .id(toolName + "-" + System.nanoTime())
            .name(toolName)
            .arguments(jsonArguments)
            .build();
        var result = client.executeTool(request);
        return result.resultText();
    }
}
