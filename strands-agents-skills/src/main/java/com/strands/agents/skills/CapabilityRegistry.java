package com.strands.agents.skills;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.nio.file.Files;
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
                var transport = mcp.toTransport();
                var client = DefaultMcpClient.builder().transport(transport).build();
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

    public record McpServerConfig(String name, String command, List<String> args, String url) {
        public McpServerConfig {
            if (command == null && url == null)
                throw new IllegalArgumentException("command or url required for MCP server " + name);
        }

        dev.langchain4j.mcp.client.transport.McpTransport toTransport() {
            if (url != null) {
                return StreamableHttpMcpTransport.builder().url(url).build();
            }
            var cmd = new ArrayList<String>();
            cmd.add(command);
            if (args != null) cmd.addAll(args);
            return StdioMcpTransport.builder().command(cmd).build();
        }
    }

    public static class Builder {
        private final List<Path> skillDirectories = new ArrayList<>();
        private final List<McpServerConfig> mcpServers = new ArrayList<>();

        public Builder skillDir(Path dir) { skillDirectories.add(dir); return this; }
        public Builder mcpServer(String name, String command, List<String> args) {
            mcpServers.add(new McpServerConfig(name, command, args, null));
            return this;
        }
        public Builder mcpServer(String name, String url) {
            mcpServers.add(new McpServerConfig(name, null, null, url));
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
