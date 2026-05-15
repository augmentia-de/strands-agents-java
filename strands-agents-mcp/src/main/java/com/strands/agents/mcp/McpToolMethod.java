package com.strands.agents.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strands.agents.core.ToolRegistry.ToolMethod;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Map;

public record McpToolMethod(McpClient client, String toolName, ToolSpecification spec)
        implements ToolMethod {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String execute(String jsonArguments) throws Exception {
        Map<String, Object> args = MAPPER.readValue(
            jsonArguments, new TypeReference<Map<String, Object>>() {});
        return client.executeTool(toolName, args);
    }
}
