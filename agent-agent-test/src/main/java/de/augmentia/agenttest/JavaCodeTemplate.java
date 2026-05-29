package de.augmentia.agenttest;

public class JavaCodeTemplate {

    public static final String CODE = """
package de.augmentia.generated;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.*;
import de.augmentia.strandsagents.core.config.*;
import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.core.tools.McpToolMethod;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.util.LinkedHashMap;
import java.util.Set;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GenTest {
    public static void main(String[] args) throws Exception {
        var apiKey = System.getenv("OPENAI_API_KEY");
        ChatModel model;
        if (apiKey != null && !apiKey.isBlank()) {
            model = ModelFactory.createOpenAiFromEnv();
        } else {
            model = new MockChatModel();
        }

        var mcpUrl = System.getenv("MCP_SERVER_URL");
        var transport = StreamableHttpMcpTransport.builder()
            .url(mcpUrl).logRequests(false).logResponses(false).build();
        var mcpClient = DefaultMcpClient.builder().transport(transport).build();
        var prefix = "mcp_localhost_8099_";
        var registry = new ToolRegistry();

        var selectedTools = Set.of(
            ${REGISTRY_TOOLS}
        );

        for (String toolName : selectedTools) {
            for (ToolSpecification spec : mcpClient.listTools()) {
                if ((prefix + spec.name()).equals(toolName)) {
                    registry.register(spec.name(), spec,
                        new McpToolMethod(mcpClient, mcpUrl, spec.name(), spec));
                }
            }
        }

        var agent = new Agent(model, registry, new ToolExecutor());

        ${STEPS_EXECUTION}

        var out = new LinkedHashMap<String, Object>();
        ${RESULTS_COLLECTION}

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, out);
    }
}
""";
}
