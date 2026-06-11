package de.augmentia.strandsagents.features.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.McpToolMethod;
import de.augmentia.strandsagents.features.tools.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public record McpIngestTool(ToolRegistry toolRegistry)
    implements AgentTool<McpIngestTool.Params> {

    public record Params(String serverName, String url) {
        public Params {
            if (url == null || url.isBlank())
                throw new IllegalArgumentException("url required for MCP server");
        }
    }

    @Override
    public String name() { return "mcp_ingest"; }

    @Override
    public String description() {
        return "Connect to an MCP server via SSE and register its tools dynamically. "
            + "Provide the SSE endpoint URL. "
            + "Once connected, the server's tools become available with the prefix 'mcp_<serverName>_'.";
    }

    @Override
    public Class<Params> parameterType() { return Params.class; }

    @Override
    public JsonNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();

        var nameProp = factory.objectNode();
        nameProp.put("type", "string");
        nameProp.put("description", "Name for this MCP server connection");
        props.set("serverName", nameProp);

        var urlProp = factory.objectNode();
        urlProp.put("type", "string");
        urlProp.put("description", "SSE endpoint URL (e.g. http://localhost:3000/sse)");
        props.set("url", urlProp);

        schema.set("properties", props);
        var required = factory.arrayNode();
        required.add("serverName");
        required.add("url");
        schema.set("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        try {
            var transport = HttpMcpTransport.builder().sseUrl(params.url()).build();
            var client = DefaultMcpClient.builder()
                .transport(transport)
                .clientName("strands-agent")
                .clientVersion("1.0")
                .protocolVersion("2024-11-05")
                .build();
            var tools = client.listTools();

            var registered = new ArrayList<String>();
            var prefix = "mcp_" + params.serverName();
            for (var spec : tools) {
                var prefixedName = prefix + "_" + spec.name();
                var prefixedSpec = ToolSpecification.builder()
                    .name(prefixedName)
                    .description(spec.description())
                    .parameters(spec.parameters())
                    .build();
                toolRegistry.register(prefixedName, prefixedSpec,
                    new McpToolMethod(client, params.serverName(), spec.name(), prefixedSpec));
                registered.add(prefixedName);
            }

            var sb = new StringBuilder();
            sb.append("Connected to MCP server '").append(params.serverName()).append("'.\n");
            sb.append("Registered ").append(registered.size()).append(" tool");
            if (registered.size() != 1) sb.append("s");
            sb.append(": ").append(String.join(", ", registered)).append("\n");
            sb.append("Use these tools with their full names as shown above.");

            return ToolResult.success(sb.toString());
        } catch (Exception e) {
            return ToolResult.error("Failed to connect MCP server '" + params.serverName() + "': " + e.getMessage());
        }
    }
}
