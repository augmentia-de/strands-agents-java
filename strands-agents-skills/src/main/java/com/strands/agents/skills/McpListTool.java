package com.strands.agents.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.strands.agents.core.tools.AgentTool;
import com.strands.agents.core.tools.ToolResult;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public record McpListTool(List<CapabilityRegistry.McpServerConfig> servers)
    implements AgentTool<McpListTool.Params> {

    public record Params(String serverName) {}

    @Override
    public String name() { return "mcp_list"; }

    @Override
    public String description() {
        return "List tools available from configured MCP servers. "
            + "Use serverName to filter a specific server, or omit to list all servers.";
    }

    @Override
    public Class<Params> parameterType() { return Params.class; }

    @Override
    public JsonNode parameterSchema() {
        var factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        schema.put("type", "object");
        var props = factory.objectNode();
        var serverProp = factory.objectNode();
        serverProp.put("type", "string");
        serverProp.put("description", "Optional server name to filter");
        props.set("serverName", serverProp);
        schema.set("properties", props);
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        var sb = new StringBuilder();
        for (var config : servers) {
            if (params.serverName() != null && !params.serverName().equals(config.name())) continue;
            sb.append("Server: ").append(config.name()).append("\n");
            try {
                var transport = config.toTransport();
                var client = DefaultMcpClient.builder().transport(transport).build();
                var tools = client.listTools();
                if (tools.isEmpty()) {
                    sb.append("  (no tools)\n");
                } else {
                    for (var spec : tools) {
                        sb.append("  - ").append(spec.name());
                        if (spec.description() != null && !spec.description().isBlank()) {
                            sb.append(": ").append(spec.description());
                        }
                        sb.append("\n");
                    }
                }
                client.close();
            } catch (Exception e) {
                sb.append("  Error: ").append(e.getMessage()).append("\n");
            }
        }
        if (servers.isEmpty()) {
            sb.append("No MCP servers configured.");
        }
        return ToolResult.success(sb.toString());
    }
}
