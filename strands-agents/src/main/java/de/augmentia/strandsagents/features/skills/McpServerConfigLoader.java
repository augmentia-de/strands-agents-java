package de.augmentia.strandsagents.features.skills;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McpServerConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
                var type = cfg.has("type") ? cfg.get("type").asText() : null;
                var transportType = "streamable-http".equalsIgnoreCase(type)
                    || "streamable".equalsIgnoreCase(type)
                    ? CapabilityRegistry.TransportType.STREAMABLE_HTTP
                    : CapabilityRegistry.TransportType.SSE;
                var clientConfig = new HashMap<String, Object>();
                cfg.fieldNames().forEachRemaining(key -> {
                    if (!"url".equals(key) && !"type".equals(key)) {
                        clientConfig.put(key, MAPPER.convertValue(cfg.get(key), Object.class));
                    }
                });
                results.add(new CapabilityRegistry.McpServerConfig(name, url, transportType, type, clientConfig));
            }
            return results;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load MCP server config from " + configPath, e);
        }
    }

    private McpServerConfigLoader() {}
}
