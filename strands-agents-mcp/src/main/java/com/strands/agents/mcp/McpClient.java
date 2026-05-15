package com.strands.agents.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.*;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.*;

public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CACHE_TTL_MS = 60_000;

    private final McpTransport transport;
    private final Map<String, CachedTool> toolCache = new ConcurrentHashMap<>();
    private final long cacheTtlMs;
    private long nextId = 1;

    public McpClient(McpTransport transport) {
        this(transport, CACHE_TTL_MS);
    }

    McpClient(McpTransport transport, long cacheTtlMs) {
        this.transport = transport;
        this.cacheTtlMs = cacheTtlMs;
    }

    public void connect() throws Exception {
        transport.connect();
        var initRequest = new JsonRpcMessage.Request(
            nextId++, "initialize",
            Map.of("protocolVersion", "2024-11-05",
                   "capabilities", Map.of(),
                   "clientInfo", Map.of("name", "strands-agents-java", "version", "1.0.0")));
        var initResponse = JsonRpcMessage.Response.fromJson(
            transport.sendAndReceive(initRequest.toJson()));
        if (initResponse.error() != null) {
            throw new RuntimeException("MCP init failed: " + initResponse.error().message());
        }

        var notifRequest = new JsonRpcMessage.Request(
            nextId++, "notifications/initialized", Map.of());
        transport.sendAndReceive(notifRequest.toJson());
    }

    public List<McpTool> listTools() throws Exception {
        purgeCache();
        var cached = toolCache.values().stream()
            .filter(c -> !c.isExpired())
            .map(CachedTool::tool)
            .toList();
        if (!cached.isEmpty()) return cached;

        var request = new JsonRpcMessage.Request(nextId++, "tools/list", Map.of());
        var response = JsonRpcMessage.Response.fromJson(
            transport.sendAndReceive(request.toJson()));
        if (response.error() != null) {
            throw new RuntimeException("MCP tools/list failed: " + response.error().message());
        }

        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) response.result();
        var toolsJson = MAPPER.writeValueAsString(resultMap.get("tools"));
        List<McpTool> tools = MAPPER.readValue(toolsJson, new TypeReference<List<McpTool>>() {});

        var now = System.currentTimeMillis();
        for (var tool : tools) {
            toolCache.put(tool.name(), new CachedTool(tool, now + cacheTtlMs));
        }
        return tools;
    }

    public String executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        var request = new JsonRpcMessage.Request(
            nextId++, "tools/call",
            Map.of("name", toolName, "arguments", arguments));
        var response = JsonRpcMessage.Response.fromJson(
            transport.sendAndReceive(request.toJson()));
        if (response.error() != null) {
            throw new RuntimeException("MCP tools/call failed: " + response.error().message());
        }

        @SuppressWarnings("unchecked")
        var resultMap = (Map<String, Object>) response.result();
        var contentList = (List<Map<String, Object>>) resultMap.get("content");
        if (contentList == null || contentList.isEmpty()) return "";
        var first = contentList.get(0);
        var text = first.get("text");
        return text != null ? text.toString() : "";
    }

    public ToolSpecification toToolSpecification(McpTool mcpTool) {
        var schemaBuilder = JsonObjectSchema.builder();
        if (mcpTool.inputSchema() != null) {
            @SuppressWarnings("unchecked")
            var properties = (Map<String, Object>) mcpTool.inputSchema().get("properties");
            if (properties != null) {
                for (var entry : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    var propDef = (Map<String, Object>) entry.getValue();
                    var type = propDef != null ? (String) propDef.get("type") : "string";
                    schemaBuilder.addProperty(entry.getKey(), toJsonSchemaElement(type));
                }
            }
        }
        return ToolSpecification.builder()
            .name(mcpTool.name())
            .description(mcpTool.description() != null ? mcpTool.description() : "")
            .parameters(schemaBuilder.build())
            .build();
    }

    private static JsonSchemaElement toJsonSchemaElement(String type) {
        return switch (type) {
            case "string" -> new JsonStringSchema();
            case "integer", "number" -> new JsonIntegerSchema();
            case "boolean" -> new JsonBooleanSchema();
            case "array" -> JsonArraySchema.builder().build();
            case "object" -> JsonObjectSchema.builder().build();
            default -> new JsonStringSchema();
        };
    }

    public void close() {
        try { transport.close(); } catch (Exception ignored) {}
    }

    private void purgeCache() {
        toolCache.values().removeIf(CachedTool::isExpired);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record McpTool(String name, String description, Map<String, Object> inputSchema) {}

    private record CachedTool(McpTool tool, long expiresAt) {
        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }
}
