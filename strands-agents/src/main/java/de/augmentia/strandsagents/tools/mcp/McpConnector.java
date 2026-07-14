package de.augmentia.strandsagents.tools.mcp;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.model.api.ToolInfo;
import de.augmentia.strandsagents.tools.McpToolMethod;
import de.augmentia.strandsagents.skills.CapabilityRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects to MCP (Model Context Protocol) servers and registers their tools with the local tool registry.
 */
public class McpConnector {

    private static final Logger log = LoggerFactory.getLogger(McpConnector.class);

    private McpConnector() {}

    /**
     * Connects to an MCP server and registers its tools under a prefixed namespace.
     */
    public static McpClient connect(CapabilityRegistry.McpServerConfig config,
                                     ToolRegistry registry, Set<String> selectedTools) throws Exception {
        var client = config.toDirectClient();
        var tools = client.listTools();
        var prefix = prefix(config);
        int registered = 0;
        for (var spec : tools) {
            var prefixedName = prefix + "_" + spec.name();
            if (selectedTools != null && !selectedTools.contains(prefixedName)) continue;
            var prefixedSpec = ToolSpecification.builder()
                .name(prefixedName)
                .description(spec.description())
                .parameters(spec.parameters())
                .build();
            registry.register(prefixedName, prefixedSpec,
                new McpToolMethod(client, config.name(), spec.name(), prefixedSpec));
            registered++;
        }
        log.info("MCP verbunden: {} ({}/{} Tools registriert)", config.name(), registered, tools.size());
        return client;
    }

    /**
     * Discovers and returns available tools from an MCP server without registering them.
     */
    public static List<ToolInfo> discoverTools(CapabilityRegistry.McpServerConfig config) {
        try {
            var client = config.toDirectClient();
            var tools = client.listTools();
            client.close();
            var prefix = prefix(config);
            return tools.stream()
                .map(spec -> {
                    var info = new ToolInfo();
                    info.name = prefix + "_" + spec.name();
                    info.description = spec.description() != null ? spec.description() : "";
                    info.parameters = spec.parameters() != null ? spec.parameters().toString() : "";
                    return info;
                })
                .toList();
        } catch (Exception e) {
            log.warn("MCP-Verbindung fehlgeschlagen: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds a sanitized prefix string from an MCP server configuration name.
     */
    public static String prefix(CapabilityRegistry.McpServerConfig config) {
        return "mcp_" + config.name().replaceAll("[^a-zA-Z0-9]", "_");
    }
}
