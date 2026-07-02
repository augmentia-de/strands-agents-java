package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import de.augmentia.strandsagents.skills.CapabilityRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapabilityRegistryTest {

    @Test
    void discoverAll_emptyRegistry_returnsEmptyList() {
        var registry = new CapabilityRegistry(List.of(), List.of());
        assertThat(registry.discoverAll()).isEmpty();
    }

    @Test
    void discoverAll_withSkillDirectories_returnsCapabilities(@TempDir Path tempDir) throws IOException {
        var skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        var mySkill = skillDir.resolve("my-skill");
        Files.createDirectories(mySkill);
        Files.writeString(mySkill.resolve("SKILL.md"), """
            ---
            name: my-skill
            description: A test skill
            ---
            Do something useful.
            """);

        var registry = new CapabilityRegistry(List.of(skillDir), List.of());
        var caps = registry.discoverAll();

        assertThat(caps).isNotEmpty();
        assertThat(caps).anyMatch(c -> c.name().equals("my-skill")
            && c.type() == CapabilityRegistry.CapabilityType.SKILL);
    }

    @Test
    void discoverAll_skillDirectoryError_returnsErrorCapability(@TempDir Path tempDir) {
        var nonExistent = tempDir.resolve("does-not-exist");
        var registry = new CapabilityRegistry(List.of(nonExistent), List.of());
        var caps = registry.discoverAll();

        assertThat(caps).anyMatch(c -> c.name().equals("error"));
    }

    @Test
    void builder_createsRegistryFromScratch(@TempDir Path tempDir) throws IOException {
        var skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        var skill = skillDir.resolve("builder-skill");
        Files.createDirectories(skill);
        Files.writeString(skill.resolve("SKILL.md"), """
            ---
            name: builder-skill
            description: Created via builder
            ---
            instructions
            """);

        var registry = CapabilityRegistry.builder()
            .skillDir(skillDir)
            .build();

        var caps = registry.discoverAll();
        assertThat(caps).anyMatch(c -> c.name().equals("builder-skill"));
    }

    @Test
    void builder_empty_createsEmptyRegistry() {
        var registry = CapabilityRegistry.builder().build();
        assertThat(registry.discoverAll()).isEmpty();
    }

    @Test
    void getServer_byName_returnsConfig() {
        var config = new CapabilityRegistry.McpServerConfig("my-server", "http://localhost:8080/sse");
        var registry = new CapabilityRegistry(List.of(), List.of(config));

        var found = registry.getServer("my-server");
        assertThat(found).isNotNull();
        assertThat(found.name()).isEqualTo("my-server");
    }

    @Test
    void getServer_unknownName_returnsNull() {
        var config = new CapabilityRegistry.McpServerConfig("my-server", "http://localhost:8080/sse");
        var registry = new CapabilityRegistry(List.of(), List.of(config));

        assertThat(registry.getServer("unknown")).isNull();
        assertThat(registry.getServer(null)).isNull();
    }

    @Test
    void mcpServerConfig_invalidUrl_throwsException() {
        assertThatThrownBy(() -> new CapabilityRegistry.McpServerConfig("bad", ""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CapabilityRegistry.McpServerConfig("bad", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discoverSkills_withSkillDirectories_returnsSkillCapabilities(@TempDir Path tempDir) throws IOException {
        var skillDir = tempDir.resolve("skills");
        Files.createDirectories(skillDir);
        var mySkill = skillDir.resolve("my-skill");
        Files.createDirectories(mySkill);
        Files.writeString(mySkill.resolve("SKILL.md"), """
            ---
            name: my-skill
            description: A test skill
            ---
            Do something useful.
            """);

        var registry = new CapabilityRegistry(List.of(skillDir), List.of());
        var caps = registry.discoverSkills();

        assertThat(caps).isNotEmpty();
        assertThat(caps).allMatch(c -> c.type() == CapabilityRegistry.CapabilityType.SKILL);
        assertThat(caps).anyMatch(c -> c.name().equals("my-skill"));
    }

    @Test
    void discoverSkills_emptyRegistry_returnsEmptyList() {
        var registry = new CapabilityRegistry(List.of(), List.of());
        assertThat(registry.discoverSkills()).isEmpty();
    }

    @Test
    void discoverTools_emptyRegistry_returnsEmptyList() {
        var registry = new CapabilityRegistry(List.of(), List.of());
        assertThat(registry.discoverTools()).isEmpty();
    }
}
