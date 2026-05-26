package de.augmentia.strandsagents.mcp.server;

import de.augmentia.strandsagents.mcp.server.tools.McpBashTool;
import de.augmentia.strandsagents.mcp.server.tools.McpEditTool;
import de.augmentia.strandsagents.mcp.server.tools.McpFindTool;
import de.augmentia.strandsagents.mcp.server.tools.McpGrepTool;
import de.augmentia.strandsagents.mcp.server.tools.McpLsTool;
import de.augmentia.strandsagents.mcp.server.tools.McpReadTool;
import de.augmentia.strandsagents.mcp.server.tools.McpWebFetchTool;
import de.augmentia.strandsagents.mcp.server.tools.McpWebSearchTool;
import de.augmentia.strandsagents.mcp.server.tools.McpWriteTool;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class McpToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistrar.class);

    @Inject
    @ConfigProperty(name = "strands.mcp.server.cwd")
    Optional<String> cwdConfig;

    void registerAll(@Observes StartupEvent ev, ToolManager toolManager) {
        var cwd = cwdConfig
            .map(Path::of)
            .orElse(Path.of("").toAbsolutePath())
            .toAbsolutePath()
            .normalize();

        ToolScanner.registerTools(toolManager,
            new McpBashTool(cwd),
            new McpReadTool(cwd),
            new McpWriteTool(cwd),
            new McpEditTool(cwd),
            new McpFindTool(cwd),
            new McpGrepTool(cwd),
            new McpLsTool(cwd),
            new McpWebFetchTool(),
            new McpWebSearchTool());

        log.info("MCP server started with {} tool(s)", 9);
    }
}
