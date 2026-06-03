package de.augmentia.strandsagents.core.model.api;

import java.util.List;

public class AgentInitRequest {
    public List<String> tools;
    public List<String> skills;
    public List<String> initialSkills;
    public String mcpServerName;
    public List<String> mcpTools;
    public String systemPrompt;
    public List<McpServerSelection> mcpServers;
    public Boolean skillSearchEnabled;
    public Boolean mcpIngestEnabled;
    public String capabilityDirs;
    public String sessionId;
}
