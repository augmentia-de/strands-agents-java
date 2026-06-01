package de.augmentia.strandsagents.skills;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class McpServerConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<CapabilityRegistry.McpServerConfig> load(Path configPath) {
        if (!Files.exists(configPath)) {
            return List.of();
        }
        try (var reader = Files.newBufferedReader(configPath)) {
            var root = MAPPER.readTree(reader);
            if (root == null) return List.of();
            var servers = root.get("mcpServers");
            if (servers == null || servers.isEmpty()) return List.of();
            var results = new ArrayList<CapabilityRegistry.McpServerConfig>();
            var fields = servers.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                var name = field.getKey();
                var cfg = field.getValue();
                if (cfg == null) continue;
                var url = cfg.has("url") ? cfg.get("url").asText() : null;
                if (url == null || url.isBlank()) continue;
                var type = cfg.has("type") ? cfg.get("type").asText() : "sse";
                var transportType = "streamable-http".equals(type) || "streamable".equals(type)
                    ? CapabilityRegistry.TransportType.STREAMABLE_HTTP
                    : CapabilityRegistry.TransportType.SSE;
                results.add(new CapabilityRegistry.McpServerConfig(name, url, transportType));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load MCP server config from " + configPath, e);
        }
    }
}
