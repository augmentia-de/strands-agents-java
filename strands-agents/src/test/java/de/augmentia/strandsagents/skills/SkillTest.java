package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import de.augmentia.strandsagents.skills.Skill;
import org.junit.jupiter.api.Test;

class SkillTest {

    @Test
    void createValidSkill() {
        var skill = new Skill("math", "Math helper", "Do math", null, null, Map.of(), null, null, null);
        assertThat(skill.name()).isEqualTo("math");
        assertThat(skill.description()).isEqualTo("Math helper");
        assertThat(skill.instructions()).isEqualTo("Do math");
    }

    @Test
    void createSkillWithAllFields() {
        var path = Path.of("/tmp/skills/my-skill");
        var allowed = List.of("bash", "calc");
        var meta = Map.<String, Object>of("author", "me");
        var skill = new Skill("full", "Full skill", "Instructions", path, allowed, meta, "MIT", "java21", null);
        assertThat(skill.path()).isEqualTo(path);
        assertThat(skill.allowedTools()).containsExactly("bash", "calc");
        assertThat(skill.metadata()).containsEntry("author", "me");
        assertThat(skill.license()).isEqualTo("MIT");
        assertThat(skill.compatibility()).isEqualTo("java21");
    }

    @Test
    void rejectNullName() {
        assertThatThrownBy(() -> new Skill(null, "desc", "instr", null, null, Map.of(), null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectBlankName() {
        assertThatThrownBy(() -> new Skill("", "desc", "instr", null, null, Map.of(), null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectNullDescription() {
        assertThatThrownBy(() -> new Skill("name", null, "instr", null, null, Map.of(), null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectBlankDescription() {
        assertThatThrownBy(() -> new Skill("name", "", "instr", null, null, Map.of(), null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowedToolsMayBeNull() {
        var skill = new Skill("n", "d", "i", null, null, Map.of(), null, null, null);
        assertThat(skill.allowedTools()).isNull();
    }

    @Test
    void metadataDefaultsToEmpty() {
        var skill = new Skill("n", "d", "i", null, null, Map.of(), null, null, null);
        assertThat(skill.metadata()).isEmpty();
    }
}
