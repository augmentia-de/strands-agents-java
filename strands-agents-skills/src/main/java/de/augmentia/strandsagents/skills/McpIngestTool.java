package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.McpToolMethod;
import de.augmentia.strandsagents.core.tools.ToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public record McpIngestTool(ToolRegistry toolRegistry)
    implements AgentTool<McpIngestTool.Params> {

    public record Params(String serverName, String command, List<String> args, String url) {
        public Params {
            if (command == null && url == null)
                throw new IllegalArgumentException("command+args or url required");
            if (command != null && url != null)
                throw new IllegalArgumentException("Only one of command or url allowed");
        }
    }

    @Override
    public String name() { return "mcp_ingest"; }

    @Override
    public String description() {
        return "Connect to an MCP server and register its tools dynamically. "
            + "Provide command+args for stdio transport, or url for SSE transport. "
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

        var cmdProp = factory.objectNode();
        cmdProp.put("type", "string");
        cmdProp.put("description", "Command for stdio transport (e.g. npx)");
        props.set("command", cmdProp);

        var argsProp = factory.objectNode();
        argsProp.put("type", "array");
        argsProp.put("description", "Arguments for stdio command");
        argsProp.set("items", factory.objectNode().put("type", "string"));
        props.set("args", argsProp);

        var urlProp = factory.objectNode();
        urlProp.put("type", "string");
        urlProp.put("description", "URL for SSE transport (e.g. http://localhost:8080/mcp)");
        props.set("url", urlProp);

        schema.set("properties", props);
        var required = factory.arrayNode();
        required.add("serverName");
        schema.set("required", required);
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        try {
            var transport = params.url() != null
                ? StreamableHttpMcpTransport.builder().url(params.url()).build()
                : StdioMcpTransport.builder().command(buildCommand(params)).build();
            var client = DefaultMcpClient.builder().transport(transport).build();
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

    private List<String> buildCommand(Params params) {
        var cmd = new ArrayList<String>();
        cmd.add(params.command());
        if (params.args() != null) cmd.addAll(params.args());
        return cmd;
    }
}
