package de.augmentia.strandsagents.features.skills;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CapabilityRegistry {

    private final List<Path> skillDirectories;
    private final List<McpServerConfig> mcpServers;

    public CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers) {
        this.skillDirectories = List.copyOf(skillDirectories);
        this.mcpServers = List.copyOf(mcpServers);
    }

    public List<Path> skillDirectories() { return skillDirectories; }
    public List<McpServerConfig> mcpServers() { return mcpServers; }

    public McpServerConfig getServer(String name) {
        if (name == null) return null;
        return mcpServers.stream()
            .filter(s -> name.equals(s.name()))
            .findFirst().orElse(null);
    }

    public List<Capability> discoverAll() {
        var results = new ArrayList<Capability>();
        for (var dir : skillDirectories) {
            try {
                var skills = SkillParser.fromDirectory(dir);
                for (var s : skills) {
                    results.add(new Capability(s.name(), s.description(),
                        dir.toString(), CapabilityType.SKILL));
                }
            } catch (Exception e) {
                results.add(new Capability("error", "Failed to scan: " + dir + " - " + e.getMessage(),
                    dir.toString(), CapabilityType.SKILL));
            }
        }
        for (var mcp : mcpServers) {
            try {
                var client = mcp.toDirectClient();
                var tools = client.listTools();
                for (var spec : tools) {
                    results.add(new Capability(spec.name(),
                        spec.description() != null ? spec.description() : "",
                        mcp.name(), CapabilityType.MCP_TOOL));
                }
                client.close();
            } catch (Exception e) {
                results.add(new Capability("error", "Failed to connect: " + mcp.name() + " - " + e.getMessage(),
                    mcp.name(), CapabilityType.MCP_TOOL));
            }
        }
        return results;
    }

    public List<Capability> search(String query) {
        if (query == null || query.isBlank()) return discoverAll();
        var q = query.toLowerCase();
        return discoverAll().stream()
            .filter(c -> c.name().toLowerCase().contains(q)
                || c.description().toLowerCase().contains(q))
            .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Capability(String name, String description, String source, CapabilityType type) {}
    public enum CapabilityType { SKILL, MCP_TOOL }

    public enum TransportType { SSE, STREAMABLE_HTTP }

    public record McpServerConfig(String name, String url, TransportType transportType) {
        public McpServerConfig(String name, String url) {
            this(name, url, TransportType.SSE);
        }

        public McpServerConfig {
            if (url == null || url.isBlank())
                throw new IllegalArgumentException("url required for MCP server " + name);
        }

        public dev.langchain4j.mcp.client.transport.McpTransport toTransport() {
            return switch (transportType) {
                case SSE -> HttpMcpTransport.builder().sseUrl(url).build();
                case STREAMABLE_HTTP -> dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport.builder().url(url).build();
            };
        }

        public McpClient toDirectClient() {
            return DefaultMcpClient.builder()
                .transport(toTransport())
                .clientName("strands-agent")
                .clientVersion("1.0")
                .protocolVersion("2024-11-05")
                .build();
        }
    }

    public static class Builder {
        private final List<Path> skillDirectories = new ArrayList<>();
        private final List<McpServerConfig> mcpServers = new ArrayList<>();

        public Builder skillDir(Path dir) { skillDirectories.add(dir); return this; }
        public Builder mcpServer(String name, String url) {
            mcpServers.add(new McpServerConfig(name, url));
            return this;
        }
        public Builder mcpServer(McpServerConfig config) {
            mcpServers.add(config);
            return this;
        }

        public CapabilityRegistry build() {
            return new CapabilityRegistry(skillDirectories, mcpServers);
        }
    }
}
