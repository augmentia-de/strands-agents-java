package de.augmentia.strandsagents.quarkus.dto;

import java.util.List;

public class AgentInitRequest {
    public List<String> tools;
    public List<String> skills;
    public List<String> initialSkills;
    public String mcpServerName;
    public List<String> mcpTools;
    /** Multiple MCP server selections (replaces mcpServerName/mcpTools) */
    public List<McpServerSelection> mcpServers;
    // Mode 2: dynamic discovery
    public Boolean skillSearchEnabled;
    public Boolean mcpIngestEnabled;
    // Mode 3: sub-agent capability search
    public String capabilityDirs;
}
