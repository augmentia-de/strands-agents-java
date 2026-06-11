package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureConfigTest {

    @Test
    void isEnabled_knownFeature_returnsTrue() {
        var fc = new FeatureConfig(Map.of(
            "llm_logging", new FeatureConfig.FeatureToggle(true, "Logging")));
        assertThat(fc.isEnabled("llm_logging")).isTrue();
    }

    @Test
    void isEnabled_disabledFeature_returnsFalse() {
        var fc = new FeatureConfig(Map.of(
            "bash", new FeatureConfig.FeatureToggle(false, "Bash")));
        assertThat(fc.isEnabled("bash")).isFalse();
    }

    @Test
    void isEnabled_unknownFeature_returnsFalse() {
        var fc = new FeatureConfig(Map.of());
        assertThat(fc.isEnabled("nonexistent")).isFalse();
    }

    @Test
    void isEnabled_nullFeatures_returnsFalse() {
        var fc = new FeatureConfig(null);
        assertThat(fc.isEnabled("anything")).isFalse();
    }

    @Test
    void withOverride_enablesDisabledFeature() {
        var fc = new FeatureConfig(Map.of(
            "bash", new FeatureConfig.FeatureToggle(false, "Bash")));
        var overridden = fc.withOverride("bash", true);
        assertThat(overridden.isEnabled("bash")).isTrue();
        assertThat(fc.isEnabled("bash")).isFalse();
    }

    @Test
    void withOverride_disablesEnabledFeature() {
        var fc = new FeatureConfig(Map.of(
            "llm_logging", new FeatureConfig.FeatureToggle(true, "Logging")));
        var overridden = fc.withOverride("llm_logging", false);
        assertThat(overridden.isEnabled("llm_logging")).isFalse();
    }

    @Test
    void withOverride_addsNewFeature() {
        var fc = new FeatureConfig(Map.of());
        var overridden = fc.withOverride("new_feature", true);
        assertThat(overridden.isEnabled("new_feature")).isTrue();
    }

    @Test
    void load_fromClasspath_returnsFeatures() {
        var fc = FeatureConfig.load();
        assertThat(fc).isNotNull();
        assertThat(fc.isEnabled("llm_logging")).isTrue();
        assertThat(fc.isEnabled("bash")).isFalse();
        assertThat(fc.isEnabled("skill_search")).isFalse();
        assertThat(fc.isEnabled("mcp_ingest")).isFalse();
        assertThat(fc.isEnabled("hitl")).isFalse();
    }

    @Test
    void load_missingResource_returnsEmpty() {
        var fc = FeatureConfig.load("nonexistent.yaml");
        assertThat(fc).isNotNull();
        assertThat(fc.features()).isEmpty();
    }

    @Test
    void load_invalidYaml_returnsEmpty() {
        var fc = FeatureConfig.load("features.yaml");
        assertThat(fc).isNotNull();
    }

    @Test
    void featureToggle_record() {
        var toggle = new FeatureConfig.FeatureToggle(true, "test feature");
        assertThat(toggle.enabled()).isTrue();
        assertThat(toggle.description()).isEqualTo("test feature");
    }

    @Test
    void strandConfig_fromYaml_resolvesFeatures() {
        var fc = new FeatureConfig(Map.of(
            "bash", new FeatureConfig.FeatureToggle(true, ""),
            "llm_logging", new FeatureConfig.FeatureToggle(false, ""),
            "skill_search", new FeatureConfig.FeatureToggle(true, "")));
        var cfg = StrandsAgentConfig.fromYaml(fc);
        assertThat(cfg.bashAllowed()).isTrue();
        assertThat(cfg.llmLogEnabled()).isFalse();
        assertThat(cfg.skillSearchEnabled()).isTrue();
        assertThat(cfg.mcpIngestEnabled()).isFalse();
    }

    @Test
    void strandConfig_fromYaml_disabledByDefault() {
        var fc = new FeatureConfig(Map.of());
        var cfg = StrandsAgentConfig.fromYaml(fc);
        assertThat(cfg.bashAllowed()).isFalse();
        assertThat(cfg.skillSearchEnabled()).isFalse();
        assertThat(cfg.mcpIngestEnabled()).isFalse();
        assertThat(cfg.httpAllowPrivate()).isTrue();
    }
}
