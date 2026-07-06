package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.tools.builtin.BaseToolNames;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class StrandsAgentConfigTest {

    @Test
    void constructor_setsAllFields() {
        var cfg = new StrandsAgentConfig("skills", ".sessions", true, "logs/llm.log",
            java.util.List.of("skill1"), true, true, "config.json",
            "/workspace", false, true, "extra", "hitl", "email@test.com");
        assertThat(cfg.skillsDir()).isEqualTo("skills");
        assertThat(cfg.sessionDir()).isEqualTo(".sessions");
        assertThat(cfg.llmLogEnabled()).isTrue();
        assertThat(cfg.llmLogPath()).isEqualTo("logs/llm.log");
        assertThat(cfg.initialSkills()).containsExactly("skill1");
        assertThat(cfg.skillSearchEnabled()).isTrue();
        assertThat(cfg.mcpIngestEnabled()).isTrue();
        assertThat(cfg.mcpConfigPath()).isEqualTo("config.json");
        assertThat(cfg.workspace()).isEqualTo("/workspace");
        assertThat(cfg.bashAllowed()).isFalse();
        assertThat(cfg.httpAllowPrivate()).isTrue();
        assertThat(cfg.extraTools()).isEqualTo("extra");
        assertThat(cfg.hitlTools()).isEqualTo("hitl");
        assertThat(cfg.hitlEmailRecipient()).isEqualTo("email@test.com");
    }

    @Test
    void fromProperties_readsAllValues() {
        var props = new Properties();
        props.setProperty("strands.agent.skills.dir", "my-skills");
        props.setProperty("strands.agent.session.dir", "my-sessions");
        props.setProperty("strands.agent.llm-log.enabled", "false");
        props.setProperty("strands.agent.llm-log.path", "custom/log.log");
        props.setProperty("strands.agent.skills.initial", "skill_a,skill_b");
        props.setProperty("strands.agent.skills.search", "true");
        props.setProperty("strands.agent.mcp.ingest", "true");
        props.setProperty("strands.agent.mcp.config", "custom-mcp.json");
        props.setProperty("strands.agent.workspace", "/opt/work");
        props.setProperty("strands.agent.bash.allow", "true");
        props.setProperty("strands.agent.http.allow-private", "true");
        props.setProperty("strands.agent.tools", "extra1,extra2");
        props.setProperty("strands.agent.hitl.tools", "hitl1");
        props.setProperty("strands.hitl.email.recipient", "admin@test.com");

        var cfg = StrandsAgentConfig.fromProperties(props);
        assertThat(cfg.skillsDir()).isEqualTo("my-skills");
        assertThat(cfg.sessionDir()).isEqualTo("my-sessions");
        assertThat(cfg.llmLogEnabled()).isFalse();
        assertThat(cfg.llmLogPath()).isEqualTo("custom/log.log");
        assertThat(cfg.initialSkills()).containsExactly("skill_a", "skill_b");
        assertThat(cfg.skillSearchEnabled()).isTrue();
        assertThat(cfg.mcpIngestEnabled()).isTrue();
        assertThat(cfg.mcpConfigPath()).isEqualTo("custom-mcp.json");
        assertThat(cfg.workspace()).isEqualTo("/opt/work");
        assertThat(cfg.bashAllowed()).isTrue();

        // httpAllowPrivate is inverted: !Boolean.parseBoolean("true") = false
        assertThat(cfg.httpAllowPrivate()).isFalse();
        assertThat(cfg.extraTools()).isEqualTo("extra1,extra2");
        assertThat(cfg.hitlTools()).isEqualTo("hitl1");
        assertThat(cfg.hitlEmailRecipient()).isEqualTo("admin@test.com");
    }

    @Test
    void fromProperties_usesDefaults() {
        var cfg = StrandsAgentConfig.fromProperties(new Properties());
        assertThat(cfg.skillsDir()).isEqualTo("skills");
        assertThat(cfg.sessionDir()).isEqualTo(".sessions");
        assertThat(cfg.llmLogEnabled()).isTrue();
        assertThat(cfg.initialSkills()).isEmpty();
        assertThat(cfg.skillSearchEnabled()).isFalse();
        assertThat(cfg.bashAllowed()).isFalse();
        assertThat(cfg.httpAllowPrivate()).isTrue();
    }

    @Test
    void resolvedWorkspace_defaultsToCurrentDir() {
        var cfg = new StrandsAgentConfig("s", ".s", true, "l", java.util.List.of(),
            false, false, "c", "", false, true, "", "", "");
        assertThat(cfg.resolvedWorkspace()).isEqualTo(Path.of("").toAbsolutePath());
    }

    @Test
    void resolvedWorkspace_usesCustomPath() {
        var cfg = new StrandsAgentConfig("s", ".s", true, "l", java.util.List.of(),
            false, false, "c", "/tmp/test-ws", false, true, "", "", "");
        assertThat(cfg.resolvedWorkspace()).isEqualTo(Path.of("/tmp/test-ws").toAbsolutePath());
    }

    @Test
    void fromYaml_usesFeatureConfig() {
        var fc = new FeatureConfig(java.util.Map.of(
            BaseToolNames.BASH, new FeatureConfig.FeatureToggle(true, "enable bash"),
            "llm_logging", new FeatureConfig.FeatureToggle(false, "disable logging")
        ));
        var cfg = StrandsAgentConfig.fromYaml(fc);
        assertThat(cfg.bashAllowed()).isTrue();
        assertThat(cfg.llmLogEnabled()).isFalse();
    }
}
