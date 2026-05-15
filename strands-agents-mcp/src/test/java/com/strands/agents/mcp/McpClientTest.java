package com.strands.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class McpClientTest {

    @Test
    void shouldPerformHandshake() throws Exception {
        var log = new AtomicReference<String>();
        var transport = new MockTransport((msg) -> {
            if (msg.contains("initialize")) {
                log.set("init");
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport);
        client.connect();
        assertThat(log.get()).isEqualTo("init");
        client.close();
    }

    @Test
    void shouldThrowOnInitError() {
        var transport = new MockTransport((msg) ->
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-1,\"message\":\"init failed\"}}");

        var client = new McpClient(transport);
        assertThatThrownBy(client::connect)
            .hasMessageContaining("init failed");
        client.close();
    }

    @Test
    void shouldListTools() throws Exception {
        var transport = new MockTransport((msg) -> {
            if (msg.contains("tools/list")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"echo\",\"description\":\"Echoes input\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}}"
                    + "]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport, 5000);
        client.connect();
        var tools = client.listTools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("echo");
        assertThat(tools.get(0).description()).isEqualTo("Echoes input");
        client.close();
    }

    @Test
    void shouldUseCache() throws Exception {
        var callCount = new AtomicReference<>(0);
        var transport = new MockTransport((msg) -> {
            if (msg.contains("tools/list")) {
                callCount.updateAndGet(c -> c + 1);
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"cached-tool\",\"description\":\"\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}"
                    + "]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport, 5000);
        client.connect();
        client.listTools();
        client.listTools();
        assertThat(callCount.get()).isEqualTo(1);
        client.close();
    }

    @Test
    void shouldExecuteTool() throws Exception {
        var transport = new MockTransport((msg) -> {
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Hello, World!\"}]}}";
            }
            if (msg.contains("tools/list")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"greet\",\"description\":\"Greets the user\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}"
                    + "]}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport, 5000);
        client.connect();
        var result = client.executeTool("greet", Map.of());
        assertThat(result).isEqualTo("Hello, World!");
        client.close();
    }

    @Test
    void shouldThrowOnToolError() {
        var transport = new MockTransport((msg) -> {
            if (msg.contains("tools/call")) {
                return "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32000,\"message\":\"Tool execution failed\"}}";
            }
            return "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}";
        });

        var client = new McpClient(transport, 5000);
        assertThatThrownBy(() -> {
            client.connect();
            client.executeTool("bad", Map.of());
        }).hasMessageContaining("Tool execution failed");
        client.close();
    }

    @Test
    void shouldConvertMcpToolToToolSpecification() {
        var transport = new MockTransport((msg) -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}");
        var client = new McpClient(transport, 5000);

        var mcpTool = new McpClient.McpTool("add", "Adds two numbers",
            Map.of("type", "object", "properties", Map.of(
                "a", Map.of("type", "integer"),
                "b", Map.of("type", "integer"))));

        var spec = client.toToolSpecification(mcpTool);
        assertThat(spec.name()).isEqualTo("add");
        assertThat(spec.description()).isEqualTo("Adds two numbers");
        assertThat(spec.parameters()).isNotNull();
        client.close();
    }

    @Test
    void shouldPropagateTransportError() {
        var transport = new MockTransport((msg) -> { throw new RuntimeException("Connection refused"); });
        var client = new McpClient(transport, 5000);
        assertThatThrownBy(client::connect)
            .hasMessageContaining("Connection refused");
        client.close();
    }

    record MockTransport(MockHandler handler) implements McpTransport {
        interface MockHandler { String handle(String msg) throws Exception; }

        @Override
        public void connect() {}

        @Override
        public String sendAndReceive(String message) throws Exception {
            return handler.handle(message);
        }

        @Override
        public boolean isConnected() { return true; }

        @Override
        public void close() {}
    }
}
