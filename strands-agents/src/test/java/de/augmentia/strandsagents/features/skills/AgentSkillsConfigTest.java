package de.augmentia.strandsagents.features.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgentSkillsConfigTest {

    @Test
    void defaultValues() {
        var config = new AgentSkillsConfig(List.of());
        assertThat(config.skills()).isEmpty();
        assertThat(config.maxResourceFiles()).isEqualTo(20);
        assertThat(config.stateKey()).isEqualTo("agent_skills");
        assertThat(config.initialSkills()).isEmpty();
    }

    @Test
    void withInitialSkills() {
        var config = new AgentSkillsConfig(List.of(), List.of("skill-a"));
        assertThat(config.initialSkills()).containsExactly("skill-a");
    }

    @Test
    void withCustomMaxResourceFiles() {
        var config = new AgentSkillsConfig(List.of(), List.of());
        assertThat(config.maxResourceFiles()).isEqualTo(20);
    }

    @Test
    void initialSkillsMax3() {
        assertThatThrownBy(() -> new AgentSkillsConfig(List.of(), List.of("a", "b", "c", "d")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialSkillsUpTo3Allowed() {
        var config = new AgentSkillsConfig(List.of(), List.of("a", "b", "c"));
        assertThat(config.initialSkills()).hasSize(3);
    }

    @Test
    void compactConstructor() {
        var config = new AgentSkillsConfig(List.of());
        assertThat(config.skills()).isEmpty();
    }
}
