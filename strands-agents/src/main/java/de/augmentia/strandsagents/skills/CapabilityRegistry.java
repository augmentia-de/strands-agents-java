package de.augmentia.strandsagents.skills;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.tools.mcp.McpClientFactory;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and aggregates capabilities from skills, MCP servers, and built-in tools.
 */
public class CapabilityRegistry {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRegistry.class);

    private static List<ToolSpecification> standardToolSpecs;

    private static synchronized List<ToolSpecification> getStandardToolSpecs() {
        if (standardToolSpecs == null) {
            standardToolSpecs = ToolRegistry.builder().standard(false).build().getSpecifications();
        }
        return standardToolSpecs;
    }

    private final List<Path> skillDirectories;
    private final List<McpServerConfig> mcpServers;
    private final boolean includeStandardTools;
    private final List<Capability> extraDefaultTools;

    /**
     * @param skillDirectories directories to scan for skills
     * @param mcpServers       MCP server configurations
     */
    public CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers) {
        this(skillDirectories, mcpServers, false, List.of());
    }

    /**
     * @param skillDirectories     directories to scan for skills
     * @param mcpServers           MCP server configurations
     * @param includeStandardTools whether to include built-in standard tools
     */
    public CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers,
                               boolean includeStandardTools) {
        this(skillDirectories, mcpServers, includeStandardTools, List.of());
    }

    /**
     * Full constructor with optional extra default capabilities.
     */
    public CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers,
                               boolean includeStandardTools, List<Capability> extraDefaultTools) {
        this.skillDirectories = List.copyOf(skillDirectories);
        this.mcpServers = List.copyOf(mcpServers);
        this.includeStandardTools = includeStandardTools;
        this.extraDefaultTools = List.copyOf(extraDefaultTools);
    }

    /**
     * Returns the immutable list of skill directories.
     */
    public List<Path> skillDirectories() { return skillDirectories; }

    /**
     * Returns the immutable list of MCP server configurations.
     */
    public List<McpServerConfig> mcpServers() { return mcpServers; }

    /**
     * Looks up an MCP server config by name, or null if not found.
     */
    public McpServerConfig getServer(String name) {
        if (name == null) return null;
        return mcpServers.stream()
            .filter(s -> name.equals(s.name()))
            .findFirst().orElse(null);
    }

    /**
     * Whether built-in standard tools are included in discovery.
     */
    public boolean isIncludeStandardTools() { return includeStandardTools; }

    /**
     * Discovers skill capabilities from all configured skill directories.
     */
    public List<Capability> discoverSkills() {
        var results = new ArrayList<Capability>();
        var known = knownToolNames();
        for (var dir : skillDirectories) {
            try {
                var skills = SkillParser.fromDirectory(dir);
                for (var s : skills) {
                    String source = s.path() != null ? s.path().toString() : dir.toString();
                    results.add(new Capability(s.name(), s.description(),
                        source, CapabilityType.SKILL));
                    for (var dt : s.declaredTools()) {
                        if (!known.contains(dt)) {
                            log.warn("Skill '{}' declares tool '{}' which is not in the registry", s.name(), dt);
                        }
                    }
                }
            } catch (Exception e) {
                results.add(new Capability("error", "Failed to scan: " + dir + " - " + e.getMessage(),
                    dir.toString(), CapabilityType.SKILL));
            }
        }
        return results;
    }

    /**
     * Discovers tool capabilities from standard tools, extra defaults, and MCP servers.
     */
    public List<Capability> discoverTools() {
        var results = new ArrayList<Capability>();
        if (includeStandardTools) {
            for (var spec : getStandardToolSpecs()) {
                results.add(new Capability(spec.name(),
                    spec.description() != null ? spec.description() : "",
                    "default", CapabilityType.DEFAULT));
            }
        }
        results.addAll(extraDefaultTools);
        for (var mcp : mcpServers) {
            McpClient client = null;
            try {
                client = mcp.toDirectClient();
                var tools = client.listTools();
                for (var spec : tools) {
                    results.add(new Capability(spec.name(),
                        spec.description() != null ? spec.description() : "",
                        mcp.name(), CapabilityType.MCP_TOOL));
                }
            } catch (Exception e) {
                results.add(new Capability("error", "Failed to connect: " + mcp.name() + " - " + e.getMessage(),
                    mcp.name(), CapabilityType.MCP_TOOL));
            } finally {
                if (client != null) {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        }
        return results;
    }

    /**
     * Discovers all capabilities (tools then skills).
     */
    public List<Capability> discoverAll() {
        var results = new ArrayList<Capability>();
        results.addAll(discoverTools());
        results.addAll(discoverSkills());
        return results;
    }

    /**
     * Loads all Skill objects from all configured skill directories.
     */
    public List<Skill> discoverAllSkills() {
        return skillDirectories.stream()
            .flatMap(d -> {
                try { return SkillParser.fromDirectory(d).stream(); }
                catch (Exception e) { return java.util.stream.Stream.of(); }
            })
            .toList();
    }

    /**
     * Finds a skill by name across all configured directories.
     */
    public Skill getSkill(String name) {
        return discoverAllSkills().stream()
            .filter(s -> s.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns the set of known tool names from standard tools and extra defaults.
     */
    public Set<String> knownToolNames() {
        var names = getStandardToolSpecs().stream()
            .map(ToolSpecification::name)
            .collect(Collectors.toCollection(HashSet::new));
        for (var cap : extraDefaultTools) {
            names.add(cap.name());
        }
        return names;
    }

    /**
     * Creates a new builder for constructing a CapabilityRegistry.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A named capability with a description, source identifier, and type.
     */
    public record Capability(String name, String description, String source, CapabilityType type) {}

    /**
     * Classifies a capability origin: skill, MCP tool, or built-in default tool.
     */
    public enum CapabilityType { SKILL, MCP_TOOL, DEFAULT }

    /**
     * Transport protocol for MCP client-server communication.
     */
    public enum TransportType { SSE, STREAMABLE_HTTP }

    /**
     * Configuration for connecting to an MCP server, including optional SPI-based client factory.
     */
    public record McpServerConfig(String name, String url, TransportType transportType,
                                   String clientType, Map<String, Object> clientConfig) {
        /**
         * Convenience constructor defaulting transport to SSE with no client type.
         */
        public McpServerConfig(String name, String url) {
            this(name, url, TransportType.SSE, null, null);
        }

        /**
         * Convenience constructor with explicit transport type and no client type.
         */
        public McpServerConfig(String name, String url, TransportType transportType) {
            this(name, url, transportType, null, null);
        }

        public McpServerConfig {
            if (url == null || url.isBlank())
                throw new IllegalArgumentException("url required for MCP server " + name);
            if (clientConfig == null) clientConfig = Map.of();
        }

        private static McpClientFactory findMcpClientFactory(String clientType) {
            var serviceFile = "/META-INF/services/" + McpClientFactory.class.getName();
            try (var in = McpServerConfig.class.getResourceAsStream(serviceFile)) {
                if (in == null) {
                    log.debug("SPI file not found: {}", serviceFile);
                    return null;
                }
                try (var reader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        try {
                            var clazz = Class.forName(line);
                            if (McpClientFactory.class.isAssignableFrom(clazz)) {
                                var factory = (McpClientFactory) clazz.getDeclaredConstructor().newInstance();
                                if (factory.type().equalsIgnoreCase(clientType)) {
                                    return factory;
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load McpClientFactory impl: {}", line, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to read SPI file: {}", serviceFile, e);
            }
            return null;
        }

        /**
         * Creates the MCP transport based on the configured transport type.
         */
        public dev.langchain4j.mcp.client.transport.McpTransport toTransport() {
            return switch (transportType) {
                case SSE -> HttpMcpTransport.builder().sseUrl(url).build();
                case STREAMABLE_HTTP -> dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport.builder().url(url).build();
            };
        }

        /**
         * Creates a direct MCP client using the SPI factory (if configured) or the default transport client.
         */
        public McpClient toDirectClient() {
            if (clientType != null && !clientType.isBlank()) {
                var factory = findMcpClientFactory(clientType.trim());
                if (factory != null) {
                    log.info("Creating MCP client via SPI factory '{}' for '{}'",
                        factory.getClass().getName(), name);
                    return factory.create(clientConfig);
                }
                log.info("No McpClientFactory found for type '{}' – falling back to DefaultMcpClient", clientType);
            }
            return DefaultMcpClient.builder()
                .transport(toTransport())
                .clientName("strands-agent")
                .clientVersion("1.0")
                .protocolVersion("2024-11-05")
                .build();
        }
    }

    /**
     * Builder for constructing a CapabilityRegistry with fluent API.
     */
    public static class Builder {
        private final List<Path> skillDirectories = new ArrayList<>();
        private final List<McpServerConfig> mcpServers = new ArrayList<>();
        private final List<Capability> extraDefaultTools = new ArrayList<>();
        private boolean includeStandardTools = false;

        /**
         * Adds a directory to scan for skills.
         */
        public Builder skillDir(Path dir) { skillDirectories.add(dir); return this; }

        /**
         * Adds an MCP server by name and URL (SSE transport).
         */
        public Builder mcpServer(String name, String url) {
            mcpServers.add(new McpServerConfig(name, url));
            return this;
        }

        /**
         * Adds a pre-configured MCP server.
         */
        public Builder mcpServer(McpServerConfig config) {
            mcpServers.add(config);
            return this;
        }

        /**
         * Sets whether built-in standard tools should be included.
         */
        public Builder includeStandardTools(boolean include) {
            this.includeStandardTools = include;
            return this;
        }

        /**
         * Registers a default tool capability.
         */
        public Builder registerDefaultTool(String name, String description) {
            extraDefaultTools.add(new Capability(name, description, "default", CapabilityType.DEFAULT));
            return this;
        }

        /**
         * Builds the CapabilityRegistry with accumulated configuration.
         */
        public CapabilityRegistry build() {
            return new CapabilityRegistry(skillDirectories, mcpServers, includeStandardTools, extraDefaultTools);
        }
    }
}
