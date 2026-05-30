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
            "mcp_localhost_8099_webSearch",
            "mcp_localhost_8099_webFetch",
            "mcp_localhost_8099_write"
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

        agent.setSystemPrompt("You are an AI assistant with access to the following tools: mcp_localhost_8099_webSearch, mcp_localhost_8099_webFetch, mcp_localhost_8099_write. **Always** call webSearch first with a clear query such as \"latest renewable energy news 2024\". Take the **first URL** returned in the \"url\" field of the response. Then call webFetch with that URL. If webFetch succeeds, extract the first non‑empty paragraph from the returned plain‑text (the text will contain line breaks). Finally, write that paragraph to a file named \"summary.txt\" using the write tool. If the search yields no URL, retry the search with a slightly broader query like \"renewable energy article\" before giving up.");

agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema("{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\",\"format\":\"uri\",\"description\":\"The URL of the article found by the web search.\"},\"searchQuery\":{\"type\":\"string\",\"description\":\"The exact query that was used for the web search.\"}},\"required\":[\"url\",\"searchQuery\"],\"additionalProperties\":false}"));
var step1 = agent.execute("Find a recent article on renewable energy (e.g., from 2024), extract its first paragraph, and save it to a file named summary.txt.");

agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema("{\"type\":\"string\",\"description\":\"The raw text content retrieved from the URL.\"}"));
var step2 = agent.execute("Next: " + step1.finalAnswer());

agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema("{\"type\":\"object\",\"properties\":{\"filePath\":{\"type\":\"string\",\"description\":\"Path of the file where the paragraph was written (e.g., \\\"summary.txt\\\").\"},\"paragraph\":{\"type\":\"string\",\"description\":\"The first paragraph extracted from the article.\"},\"written\":{\"type\":\"boolean\",\"description\":\"Indicates whether the write operation succeeded.\"}},\"required\":[\"filePath\",\"paragraph\",\"written\"],\"additionalProperties\":false}"));
var step3 = agent.execute("Next: " + step2.finalAnswer());

        var out = new LinkedHashMap<String, Object>();
        out.put("step1", step1.finalAnswer());
out.put("step2", step2.finalAnswer());
out.put("step3", step3.finalAnswer());
out.put("stopReason", step3.stopReason().name());
out.put("toolCalls", (step1.metrics() != null ? step1.metrics().toolCallsCount() : 0) + (step2.metrics() != null ? step2.metrics().toolCallsCount() : 0) + (step3.metrics() != null ? step3.metrics().toolCallsCount() : 0));

        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, out);
    }
}
