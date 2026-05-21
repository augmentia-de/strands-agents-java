package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillParserTest {

    private static final String VALID_SKILL = """
        ---
        name: test-skill
        description: A test skill
        allowed-tools: bash grep
        license: MIT
        compatibility: java21
        metadata:
          author: test
        ---
        This is the skill instructions.
        Second line.
        """;

    private static final String MINIMAL_SKILL = """
        ---
        name: minimal
        description: Minimal skill
        ---
        Just instructions.
        """;

    // --- fromContent ---

    @Test
    void parseValidSkill() {
        var skill = SkillParser.fromContent(VALID_SKILL);
        assertThat(skill.name()).isEqualTo("test-skill");
        assertThat(skill.description()).isEqualTo("A test skill");
        assertThat(skill.instructions()).isEqualTo("This is the skill instructions.\nSecond line.");
        assertThat(skill.allowedTools()).containsExactly("bash", "grep");
        assertThat(skill.license()).isEqualTo("MIT");
        assertThat(skill.compatibility()).isEqualTo("java21");
        assertThat(skill.metadata()).containsEntry("author", "test");
    }

    @Test
    void parseMinimalSkill() {
        var skill = SkillParser.fromContent(MINIMAL_SKILL);
        assertThat(skill.name()).isEqualTo("minimal");
        assertThat(skill.description()).isEqualTo("Minimal skill");
        assertThat(skill.instructions()).isEqualTo("Just instructions.");
        assertThat(skill.allowedTools()).isNull();
        assertThat(skill.metadata()).isEmpty();
    }

    @Test
    void rejectMissingOpeningDelimiter() {
        assertThatThrownBy(() -> SkillParser.fromContent("no delimiter"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectMissingClosingDelimiter() {
        assertThatThrownBy(() -> SkillParser.fromContent("---\nname: x\n"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectMissingName() {
        assertThatThrownBy(() -> SkillParser.fromContent("---\ndescription: d\n---\nbody"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectMissingDescription() {
        assertThatThrownBy(() -> SkillParser.fromContent("---\nname: n\n---\nbody"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectInvalidYaml() {
        assertThatThrownBy(() -> SkillParser.fromContent("---\n: invalid yaml\n---\nbody"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseWithAllowedToolsAsList() {
        var content = """
            ---
            name: list-tools
            description: Tools as list
            allowed_tools:
              - a
              - b
            ---
            body
            """;
        var skill = SkillParser.fromContent(content);
        assertThat(skill.allowedTools()).containsExactly("a", "b");
    }

    @Test
    void parseWithEmptyAllowedTools() {
        var content = """
            ---
            name: no-tools
            description: No tools
            allowed-tools:
            ---
            body
            """;
        var skill = SkillParser.fromContent(content);
        assertThat(skill.allowedTools()).isNull();
    }

    @Test
    void instructionsPreservesWhitespace() {
        var content = """
            ---
            name: ws
            description: Whitespace test
            ---
            Line 1

            Line 3
            """;
        var skill = SkillParser.fromContent(content);
        assertThat(skill.instructions()).isEqualTo("Line 1\n\nLine 3");
    }

    // --- fromFile with @TempDir ---

    @Test
    void fromFileReadsSkillMdInDirectory(@TempDir Path tempDir) throws IOException {
        var skillDir = tempDir.resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), VALID_SKILL);

        var skill = SkillParser.fromFile(skillDir);
        assertThat(skill.name()).isEqualTo("test-skill");
        assertThat(skill.path()).isEqualTo(skillDir);
    }

    @Test
    void fromFileReadsExplicitPath(@TempDir Path tempDir) throws IOException {
        var skillFile = tempDir.resolve("SKILL.md");
        Files.writeString(skillFile, MINIMAL_SKILL);

        var skill = SkillParser.fromFile(skillFile);
        assertThat(skill.name()).isEqualTo("minimal");
        assertThat(skill.path()).isEqualTo(tempDir);
    }

    @Test
    void fromFileRejectsMissingSkillMd(@TempDir Path tempDir) throws IOException {
        var emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        assertThatThrownBy(() -> SkillParser.fromFile(emptyDir))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromFileRejectsNonSkillMdFile(@TempDir Path tempDir) throws IOException {
        var f = tempDir.resolve("other.md");
        Files.writeString(f, "content");
        assertThatThrownBy(() -> SkillParser.fromFile(f))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- fromDirectory ---

    @Test
    void fromDirectoryScansSubdirectories(@TempDir Path tempDir) throws IOException {
        var dir1 = tempDir.resolve("skill-a");
        Files.createDirectories(dir1);
        Files.writeString(dir1.resolve("SKILL.md"), VALID_SKILL);

        var dir2 = tempDir.resolve("skill-b");
        Files.createDirectories(dir2);
        Files.writeString(dir2.resolve("SKILL.md"), MINIMAL_SKILL);

        var skills = SkillParser.fromDirectory(tempDir);
        assertThat(skills).hasSize(2);
        assertThat(skills).extracting(Skill::name).containsExactlyInAnyOrder("test-skill", "minimal");
    }

    @Test
    void fromDirectorySkipsInvalidDirs(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("valid"));
        Files.writeString(tempDir.resolve("valid").resolve("SKILL.md"), VALID_SKILL);

        Files.createDirectories(tempDir.resolve("empty"));

        var skills = SkillParser.fromDirectory(tempDir);
        assertThat(skills).hasSize(1);
    }

    @Test
    void fromDirectoryReturnsEmptyForNoSkills(@TempDir Path tempDir) throws IOException {
        var skills = SkillParser.fromDirectory(tempDir);
        assertThat(skills).isEmpty();
    }

    // --- fromUrl ---

    @Test
    void fromUrlFailsForInvalidUrl() {
        assertThatThrownBy(() -> SkillParser.fromUrl("http://localhost:1/nonexistent").join())
            .isInstanceOf(RuntimeException.class);
    }
}
