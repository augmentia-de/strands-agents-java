package de.augmentia.strandsagents.skills;

import java.util.List;

public record AgentSkillsConfig(
    List<Skill> skills,
    int maxResourceFiles,
    String stateKey,
    List<String> initialSkills

) {
    public AgentSkillsConfig(List<Skill> skills) {
        this(skills, 20, "agent_skills", List.of());
    }

    public AgentSkillsConfig(List<Skill> skills, List<String> initialSkills) {
        this(skills, 20, "agent_skills", initialSkills);
    }

    public AgentSkillsConfig {
        if (initialSkills != null && initialSkills.size() > 3) {
            throw new IllegalArgumentException("initialSkills max 3");
        }
    }
}
