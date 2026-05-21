package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {

    // --- Builder ---

    @Test
    void builderCreatesEmptyRegistry() {
        var reg = CapabilityRegistry.builder().build();
        assertThat(reg.skillDirectories()).isEmpty();
        assertThat(reg.mcpServers()).isEmpty();
    }

    @Test
    void builderWithSkillDir() {
        var reg = CapabilityRegistry.builder()
            .skillDir(Path.of("/tmp/skills"))
            .build();
        assertThat(reg.skillDirectories()).containsExactly(Path.of("/tmp/skills"));
    }

    @Test
    void builderWithMcpServerByUrl() {
        var reg = CapabilityRegistry.builder()
            .mcpServer("test", "http://localhost:8080/mcp")
            .build();
        assertThat(reg.mcpServers()).hasSize(1);
        assertThat(reg.mcpServers().get(0).name()).isEqualTo("test");
        assertThat(reg.mcpServers().get(0).url()).isEqualTo("http://localhost:8080/mcp");
    }

    @Test
    void builderWithMcpServerByCommand() {
        var reg = CapabilityRegistry.builder()
            .mcpServer("test", "npx", List.of("-y", "@modelcontextprotocol/server-filesystem"))
            .build();
        assertThat(reg.mcpServers()).hasSize(1);
        assertThat(reg.mcpServers().get(0).command()).isEqualTo("npx");
        assertThat(reg.mcpServers().get(0).args()).containsExactly("-y", "@modelcontextprotocol/server-filesystem");
    }

    @Test
    void builderWithMcpServerConfig() {
        var config = new CapabilityRegistry.McpServerConfig("cfg", "cmd", List.of("a"), null);
        var reg = CapabilityRegistry.builder().mcpServer(config).build();
        assertThat(reg.mcpServers()).hasSize(1);
    }

    @Test
    void builderReturnsImmutableLists() {
        var reg = CapabilityRegistry.builder().build();
        assertThatThrownBy(() -> reg.skillDirectories().add(Path.of("/x")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- McpServerConfig validation ---

    @Test
    void mcpServerConfigRequiresCommandOrUrl() {
        assertThatThrownBy(() -> new CapabilityRegistry.McpServerConfig("bad", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mcpServerConfigAcceptsCommand() {
        var cfg = new CapabilityRegistry.McpServerConfig("ok", "echo", List.of("hi"), null);
        assertThat(cfg.command()).isEqualTo("echo");
    }

    @Test
    void mcpServerConfigAcceptsUrl() {
        var cfg = new CapabilityRegistry.McpServerConfig("ok", null, null, "http://localhost/mcp");
        assertThat(cfg.url()).isEqualTo("http://localhost/mcp");
    }

    // --- Capability record ---

    @Test
    void capabilityValues() {
        var cap = new CapabilityRegistry.Capability("n", "d", "src", CapabilityRegistry.CapabilityType.SKILL);
        assertThat(cap.name()).isEqualTo("n");
        assertThat(cap.description()).isEqualTo("d");
        assertThat(cap.source()).isEqualTo("src");
        assertThat(cap.type()).isEqualTo(CapabilityRegistry.CapabilityType.SKILL);
    }

    @Test
    void capabilityTypeValues() {
        assertThat(CapabilityRegistry.CapabilityType.values())
            .containsExactly(CapabilityRegistry.CapabilityType.SKILL, CapabilityRegistry.CapabilityType.MCP_TOOL);
    }

    // --- search on empty registry ---

    @Test
    void searchOnEmptyRegistryReturnsEmpty() {
        var reg = CapabilityRegistry.builder().build();
        var results = reg.search("anything");
        assertThat(results).isEmpty();
    }

    @Test
    void searchWithNullQueryReturnsAll() {
        var reg = CapabilityRegistry.builder().build();
        var results = reg.search(null);
        assertThat(results).isEmpty();
    }

    // --- discoverAll on empty registry ---

    @Test
    void discoverAllOnEmptyReturnsEmpty() {
        var reg = CapabilityRegistry.builder().build();
        var results = reg.discoverAll();
        assertThat(results).isEmpty();
    }
}
