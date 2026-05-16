package com.strands.agents.quarkus.dto;

import java.util.List;

public class AgentInitRequest {
    public List<String> tools;
    public List<String> skills;
    public List<String> initialSkills;
    public String mcpUrl;
    public List<String> mcpTools;
    // Mode 2: dynamic discovery
    public Boolean skillSearchEnabled;
    public Boolean mcpIngestEnabled;
    // Mode 3: sub-agent capability search
    public String capabilityDirs;
    public String capabilityMcp;
}
