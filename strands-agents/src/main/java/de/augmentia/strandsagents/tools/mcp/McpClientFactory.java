package de.augmentia.strandsagents.tools.mcp;

import dev.langchain4j.mcp.client.McpClient;
import java.util.Map;

public interface McpClientFactory {
    String type();
    McpClient create(Map<String, Object> config);
}
