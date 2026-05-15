package com.strands.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.strands.agents.core.ToolExecutor;
import com.strands.agents.core.ToolRegistry;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpIntegrationTest {

    @Test
    void fullRoundTripWithToolExecutor() throws Exception {
        var transport = new McpClientTest.MockTransport((msg) -> {
            if (msg.contains("initialize")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}";
            }
            if (msg.contains("tools/list")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"uppercase\",\"description\":\"Converts text to uppercase\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}}"
                    + "]}}";
            }
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"HELLO\"}]}}";
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

        var executor = new ToolExecutor();
        var request = ToolExecutionRequest.builder()
            .id("req-1")
            .name("uppercase")
            .arguments("{\"text\": \"hello\"}")
            .build();

        var results = executor.executeAll(List.of(request), registry);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).toolName()).isEqualTo("uppercase");
        assertThat(results.get(0).result()).isEqualTo("HELLO");
        assertThat(results.get(0).isError()).isFalse();

        client.close();
    }

    @Test
    void multipleToolsRoundTrip() throws Exception {
        var transport = new McpClientTest.MockTransport((msg) -> {
            if (msg.contains("initialize")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}";
            }
            if (msg.contains("tools/list")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"add\",\"description\":\"Adds numbers\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"
                    + "{\"name\":\"multiply\",\"description\":\"Multiplies numbers\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}"
                    + "]}}";
            }
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"result\"}]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport, 5000);
        client.connect();
        var tools = client.listTools();

        var registry = new ToolRegistry();
        for (var t : tools) {
            var spec = client.toToolSpecification(t);
            registry.register(spec.name(), spec, new McpToolMethod(client, t.name(), spec));
        }

        var executor = new ToolExecutor();
        var requests = List.of(
            ToolExecutionRequest.builder().id("r1").name("add").arguments("{}").build(),
            ToolExecutionRequest.builder().id("r2").name("multiply").arguments("{}").build()
        );

        var results = executor.executeAll(requests, registry);
        assertThat(results).hasSize(2);
        assertThat(results).extracting("toolName").containsExactlyInAnyOrder("add", "multiply");
        client.close();
    }
}
