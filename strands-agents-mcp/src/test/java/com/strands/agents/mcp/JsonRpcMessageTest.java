package com.strands.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonRpcMessageTest {

    @Test
    void shouldSerializeRequest() {
        var req = new JsonRpcMessage.Request(1, "tools/list", null);
        var json = req.toJson();
        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"method\":\"tools/list\"");
        assertThat(json).contains("\"id\":1");
    }

    @Test
    void shouldDeserializeResponse() {
        var json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}";
        var resp = JsonRpcMessage.Response.fromJson(json);
        assertThat(resp.id()).isEqualTo(1);
        assertThat(resp.error()).isNull();
        assertThat(resp.result()).isNotNull();
    }

    @Test
    void shouldDeserializeErrorResponse() {
        var json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";
        var resp = JsonRpcMessage.Response.fromJson(json);
        assertThat(resp.id()).isEqualTo(1);
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(-32601);
        assertThat(resp.error().message()).isEqualTo("Method not found");
    }

    @Test
    void shouldRoundtripRequestWithParams() {
        var req = new JsonRpcMessage.Request(42, "tools/call",
            java.util.Map.of("name", "test-tool", "arguments", java.util.Map.of("key", "val")));
        var json = req.toJson();
        assertThat(json).contains("\"name\":\"test-tool\"");
    }
}
