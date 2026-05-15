package com.strands.agents.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class JsonRpcMessage {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonRpcMessage() {}

    public record Request(long id, String method, Object params) {
        public String toJson() {
            try {
                var map = new java.util.LinkedHashMap<String, Object>();
                map.put("jsonrpc", "2.0");
                map.put("id", id);
                map.put("method", method);
                if (params != null) map.put("params", params);
                return MAPPER.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("JSON-RPC Request serialization failed", e);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(long id, Object result, Error error) {
        public static Response fromJson(String json) {
            try {
                return MAPPER.readValue(json, Response.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("JSON-RPC Response deserialization failed: " + json, e);
            }
        }
    }

    public record Error(int code, String message, Object data) {}
}
