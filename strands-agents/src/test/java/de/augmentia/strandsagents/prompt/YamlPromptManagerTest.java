package de.augmentia.strandsagents.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlPromptManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void classpathResource_loadsPrompts() {
        var pm = new YamlPromptManager("prompts.yaml");
        assertThat(pm.size()).isGreaterThan(0);
    }

    @Test
    void classpathResource_unknownKey_returnsNull() {
        var pm = new YamlPromptManager("prompts.yaml");
        assertThat(pm.get("nonexistent.key")).isNull();
    }

    @Test
    void classpathResource_knownKey_returnsValue() {
        var pm = new YamlPromptManager("prompts.yaml");
        var val = pm.get("agent.max_iterations");
        assertThat(val).isNotNull().isNotEmpty();
    }

    @Test
    void classpathResource_withArgs_formatsString() {
        var pm = new YamlPromptManager("prompts.yaml");
        var val = pm.get("agent.max_iterations", "test");
        assertThat(val).isNotNull();
    }

    @Test
    void filePath_loadsPrompts() throws IOException {
        var yamlFile = tempDir.resolve("test.yaml");
        Files.writeString(yamlFile, "prompts:\n  greeting: \"Hello %s\"\n  farewell: \"Goodbye\"\n");
        var pm = new YamlPromptManager(yamlFile);
        assertThat(pm.get("greeting", "World")).isEqualTo("Hello World");
        assertThat(pm.get("farewell")).isEqualTo("Goodbye");
    }

    @Test
    void filePath_missingFile_throws() {
        var missing = tempDir.resolve("missing.yaml");
        assertThatThrownBy(() -> new YamlPromptManager(missing))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void directory_loadsAllYamlFilesSorted() throws IOException {
        var aFile = tempDir.resolve("a.yaml");
        var bFile = tempDir.resolve("b.yaml");
        Files.writeString(aFile, "prompts:\n  from_a: \"value_a\"\n");
        Files.writeString(bFile, "prompts:\n  from_b: \"value_b\"\n");
        var pm = new YamlPromptManager(tempDir, true);
        assertThat(pm.get("from_a")).isEqualTo("value_a");
        assertThat(pm.get("from_b")).isEqualTo("value_b");
    }

    @Test
    void directory_notADirectory_throws() throws IOException {
        var file = tempDir.resolve("not_a_dir.yaml");
        Files.writeString(file, "prompts:\n  k: \"v\"\n");
        assertThatThrownBy(() -> new YamlPromptManager(file, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Not a directory");
    }

    @Test
    void directoryLoadsYmlAndYamlExtensions() throws IOException {
        var ymlFile = tempDir.resolve("first.yml");
        var yamlFile = tempDir.resolve("second.yaml");
        Files.writeString(ymlFile, "prompts:\n  from_yml: \"yml_val\"\n");
        Files.writeString(yamlFile, "prompts:\n  from_yaml: \"yaml_val\"\n");
        var pm = new YamlPromptManager(tempDir, true);
        assertThat(pm.get("from_yml")).isEqualTo("yml_val");
        assertThat(pm.get("from_yaml")).isEqualTo("yaml_val");
    }
}
