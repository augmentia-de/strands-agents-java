package de.augmentia.strandsagents.quarkus.dto;

import java.util.List;

public class McpServerSelection {
    /** Name of a configured server (from MCP_SERVER_CONFIG.json) */
    public String serverName;
    /** Tool names to register (subset of the server's tools); null/empty = all */
    public List<String> tools;
    /** Custom URL (for unconfigured servers); when set, serverName is treated as label */
    public String url;
}
