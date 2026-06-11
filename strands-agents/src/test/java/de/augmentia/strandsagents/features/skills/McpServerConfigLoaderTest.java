package de.augmentia.strandsagents.features.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpServerConfigLoaderTest {

    @Test
    void load_missingFile_returnsEmptyList() {
        var configs = McpServerConfigLoader.load(Path.of("/nonexistent/config.json"));
        assertThat(configs).isEmpty();
    }

    @Test
    void load_validSseConfig_returnsServers(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            {
              "mcpServers": {
                "filesystem": {
                  "type": "sse",
                  "url": "http://localhost:3000/sse"
                }
              }
            }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).name()).isEqualTo("filesystem");
        assertThat(configs.get(0).url()).isEqualTo("http://localhost:3000/sse");
        assertThat(configs.get(0).transportType()).isEqualTo(CapabilityRegistry.TransportType.SSE);
    }

    @Test
    void load_validStreamableHttpConfig_returnsServers(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            {
              "mcpServers": {
                "api": {
                  "type": "streamable-http",
                  "url": "http://api:8080/mcp"
                }
              }
            }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).transportType()).isEqualTo(CapabilityRegistry.TransportType.STREAMABLE_HTTP);
    }

    @Test
    void load_multipleServers_returnsAll(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            {
              "mcpServers": {
                "server-a": { "url": "http://a:3000/sse" },
                "server-b": { "url": "http://b:3000/sse", "type": "sse" }
              }
            }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs).hasSize(2);
    }

    @Test
    void load_emptyServers_returnsEmptyList(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            { "mcpServers": {} }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs).isEmpty();
    }

    @Test
    void load_serverWithoutUrl_skipsEntry(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            {
              "mcpServers": {
                "broken": { "type": "sse" },
                "valid": { "url": "http://valid:3000/sse" }
              }
            }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).name()).isEqualTo("valid");
    }

    @Test
    void load_invalidJson_throwsException(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, "this is not json");

        assertThatThrownBy(() -> McpServerConfigLoader.load(configFile))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to load MCP server config");
    }

    @Test
    void load_defaultTransportIsSse(@TempDir Path tempDir) throws IOException {
        var configFile = tempDir.resolve("mcp-config.json");
        Files.writeString(configFile, """
            {
              "mcpServers": {
                "default": { "url": "http://localhost:3000/sse" }
              }
            }
            """);

        var configs = McpServerConfigLoader.load(configFile);
        assertThat(configs.get(0).transportType()).isEqualTo(CapabilityRegistry.TransportType.SSE);
    }
}
