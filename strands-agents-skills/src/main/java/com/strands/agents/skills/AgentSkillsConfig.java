package com.strands.agents.skills;

import java.util.List;

public record AgentSkillsConfig(
    List<Skill> skills,
    int maxResourceFiles,
    String stateKey
) {
    public AgentSkillsConfig(List<Skill> skills) {
        this(skills, 20, "agent_skills");
    }
}
