package com.strands.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.strands.agents.core.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolMethodTest {

    @Test
    void shouldExecuteViaMcpClient() throws Exception {
        var transport = new McpClientTest.MockTransport((msg) -> {
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"42\"}]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });
        var client = new McpClient(transport, 5000);
        client.connect();

        var spec = ToolSpecification.builder()
            .name("calculate")
            .description("Calculates something")
            .build();
        var method = new McpToolMethod(client, "calculate", spec);

        var result = method.execute("{\"x\": 1}");
        assertThat(result).isEqualTo("42");
        client.close();
    }

    @Test
    void shouldIntegrateWithToolRegistry() throws Exception {
        var transport = new McpClientTest.MockTransport((msg) -> {
            if (msg.contains("tools/list")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"mcp-tool\",\"description\":\"MCP tool\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}"
                    + "]}}";
            }
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"mcp-result\"}]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });
        var client = new McpClient(transport, 5000);
        client.connect();

        var tools = client.listTools();
        var registry = new ToolRegistry();
        for (var mcpTool : tools) {
            var spec = client.toToolSpecification(mcpTool);
            registry.register(spec.name(), spec, new McpToolMethod(client, mcpTool.name(), spec));
        }

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getToolNames()).contains("mcp-tool");

        var toolMethod = registry.get("mcp-tool");
        assertThat(toolMethod.spec().name()).isEqualTo("mcp-tool");

        var result = toolMethod.execute("{}");
        assertThat(result).isEqualTo("mcp-result");
        client.close();
    }

    @Test
    void shouldFailOnTransportError() {
        var transport = new McpClientTest.MockTransport((msg) -> { throw new RuntimeException("transport error"); });
        var client = new McpClient(transport, 5000);

        var spec = ToolSpecification.builder().name("bad").build();
        var method = new McpToolMethod(client, "bad", spec);

        assertThatThrownBy(() -> method.execute("{}"))
            .hasMessageContaining("transport error");
    }
}
