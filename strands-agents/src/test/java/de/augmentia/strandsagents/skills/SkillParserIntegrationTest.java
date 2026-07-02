package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import de.augmentia.strandsagents.skills.CapabilityRegistry;
import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.skills.SkillParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class SkillParserIntegrationTest {

    @Test
    void fromDirectory_reproducesDemoStructure(@TempDir Path tempDir) throws Exception {
        var writeSkillDir = tempDir.resolve("write-files");
        Files.createDirectories(writeSkillDir);
        Files.writeString(writeSkillDir.resolve("SKILL.md"), """
            ---
            name: write-files
            description: "Write files to the workspace (tool: write)"
            ---
            Instructions for writing files to the workspace.
            Use the 'write' tool to create or overwrite files.
            """);

        var readSkillDir = tempDir.resolve("read-files");
        Files.createDirectories(readSkillDir);
        Files.writeString(readSkillDir.resolve("SKILL.md"), """
            ---
            name: read-files
            description: "Read files from the workspace (tool: read)"
            ---
            Instructions for reading files from the workspace.
            Use the 'read' tool to read file contents.
            """);

        var skills = SkillParser.fromDirectory(tempDir);
        assertThat(skills).hasSize(2);
        assertThat(skills).extracting(Skill::name).containsExactlyInAnyOrder("write-files", "read-files");
    }

    @Test
    void discoverAll_reproducesDemoSetup(@TempDir Path tempDir) throws Exception {
        var writeSkillDir = tempDir.resolve("write-files");
        Files.createDirectories(writeSkillDir);
        Files.writeString(writeSkillDir.resolve("SKILL.md"), """
            ---
            name: write-files
            description: "Write files to the workspace (tool: write)"
            ---
            Instructions for writing files to the workspace.
            Use the 'write' tool to create or overwrite files.
            """);

        var readSkillDir = tempDir.resolve("read-files");
        Files.createDirectories(readSkillDir);
        Files.writeString(readSkillDir.resolve("SKILL.md"), """
            ---
            name: read-files
            description: "Read files from the workspace (tool: read)"
            ---
            Instructions for reading files from the workspace.
            Use the 'read' tool to read file contents.
            """);

        var registry = new CapabilityRegistry(List.of(tempDir), List.of());
        var caps = registry.discoverAll();

        assertThat(caps).anyMatch(c -> c.name().equals("write-files")
            && c.type() == CapabilityRegistry.CapabilityType.SKILL);
        assertThat(caps).anyMatch(c -> c.name().equals("read-files")
            && c.type() == CapabilityRegistry.CapabilityType.SKILL);
    }
}
