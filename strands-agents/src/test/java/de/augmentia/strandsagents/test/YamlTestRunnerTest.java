package de.augmentia.strandsagents.test;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.test.YamlTestRunner.TestResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlTestRunnerTest {

    static final Path CONFIG_DIR = Path.of("../strands-test");

    @Test
    void runner_loadsConfigAndReturnsResult() {
        var configPath = CONFIG_DIR.resolve("test-config.yaml");
        var result = YamlTestRunner.runConfig(configPath);
        assertThat(result).isNotNull();
        assertThat(result.label()).contains("variant_17");
        assertThat(result.finalAnswer()).isNotEmpty();
        assertThat(result.stopReason()).isNotNull();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void runner_assertionsPassForMainConfig() {
        var configPath = CONFIG_DIR.resolve("test-config.yaml");
        var result = YamlTestRunner.runConfig(configPath);
        assertThat(result.passed()).as("errors: " + result.errors()).isTrue();
    }

    @Test
    void runner_handlesStructuredConfig() {
        var configPath = CONFIG_DIR.resolve("test-config-structured.yaml");
        var result = YamlTestRunner.runConfig(configPath);
        assertThat(result).isNotNull();
        assertThat(result.passed()).as("errors: " + result.errors()).isTrue();
    }

    @Test
    void runner_handlesPluginsConfig() {
        var configPath = CONFIG_DIR.resolve("test-config-plugins.yaml");
        var result = YamlTestRunner.runConfig(configPath);
        assertThat(result).isNotNull();
        assertThat(result.passed()).as("errors: " + result.errors()).isTrue();
    }

    @Test
    void runner_handlesResilienceConfig() {
        var configPath = CONFIG_DIR.resolve("test-config-resilience.yaml");
        var result = YamlTestRunner.runConfig(configPath);
        assertThat(result).isNotNull();
        assertThat(result.passed()).as("errors: " + result.errors()).isTrue();
    }

    @Test
    void allConfigsPass() {
        var configs = List.of(
            CONFIG_DIR.resolve("test-config.yaml"),
            CONFIG_DIR.resolve("test-config-structured.yaml"),
            CONFIG_DIR.resolve("test-config-plugins.yaml"),
            CONFIG_DIR.resolve("test-config-resilience.yaml")
        );
        var results = new ArrayList<TestResult>();
        for (var path : configs) {
            results.add(YamlTestRunner.runConfig(path));
        }
        for (var result : results) {
            assertThat(result.passed())
                .as("Config '%s' failed: %s", result.label(), result.errors())
                .isTrue();
        }
    }

    @Test
    void missingFile_throws() {
        var missingPath = CONFIG_DIR.resolve("nonexistent.yaml");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> YamlTestRunner.runConfig(missingPath));
    }

    @Test
    void loadConfig_populatesAllFields() {
        var configPath = CONFIG_DIR.resolve("test-config.yaml");
        var config = YamlTestRunner.loadConfig(configPath);
        assertThat(config.run()).isNotNull();
        assertThat(config.run().variant()).isEqualTo(17);
        assertThat(config.model()).isNotNull();
        assertThat(config.model().type()).isEqualTo("mock");
        assertThat(config.tools()).isNotNull();
        assertThat(config.tools().preset()).isEqualTo("minimal");
        assertThat(config.resilience()).isNotNull();
        assertThat(config.plugins()).isNotNull();
        assertThat(config.hooks()).hasSize(1);
        assertThat(config.systemPrompt()).isNotEmpty();
        assertThat(config.testPrompt()).isEqualTo("What is 2+2?");
        assertThat(config.asserts()).isNotNull();
        assertThat(config.asserts().finalAnswerNotNull()).isTrue();
        assertThat(config.nextVariant()).isNotNull();
    }
}
