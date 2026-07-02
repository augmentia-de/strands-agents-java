package de.augmentia.strandsagents.quarkus;

import de.augmentia.strandsagents.skills.McpServerConfigLoader;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class McpConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionTest.class);

    @Test
    void listToolsFromConfiguredServer() throws Exception {
        var configPath = resolveConfigPath();
        log.info("Loading MCP config from: {}", configPath.toAbsolutePath());

        var servers = McpServerConfigLoader.load(configPath);
        assertThat(servers).describedAs("At least one MCP server must be configured in %s", configPath).isNotEmpty();

        for (var server : servers) {
            log.info("=== Server: {} ===", server.name());
            log.info("  url: {}", server.url());
            log.info("  transportType: {} | clientType: {}", server.transportType(), server.clientType());

            try {
                var client = server.toDirectClient();
                var tools = client.listTools();
                log.info("  tools ({}):", tools.size());
                for (var tool : tools) {
                    log.info("    - name: {}", tool.name());
                    log.info("      description: {}", tool.description());
                    if (tool.parameters() != null) {
                        log.info("      parameters: {}", tool.parameters());
                    }
                }
                assertThat(tools).isNotEmpty();
                client.close();
            } catch (Exception e) {
                log.warn("  Failed to connect: {}", e.getMessage());
            }
        }
    }

    private static Path resolveConfigPath() {
        var prop = System.getProperty("strands.agent.mcp.config");
        if (prop != null) return Path.of(prop);
        var env = System.getenv("STRANDS_MCP_CONFIG");
        if (env != null) return Path.of(env);
        return Path.of("/home/torsten/dev/my-projects/strands-agents-java-1/config/MCP_SERVER_CONFIG.json");
    }
}
