package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.skills.SkillSearchTool;
import org.junit.jupiter.api.Test;

class SkillSearchToolTest {

    private static final Skill MATH_SKILL = new Skill("math",
        "Math helper", "Do math", null, java.util.List.of("calc"), Map.of(), null, null, null);

    private static final Skill WEATHER_SKILL = new Skill("weather",
        "Weather lookup", "Check weather", null, null, Map.of(), null, null, null);

    @Test
    void toolName() {
        var tool = new SkillSearchTool(Map.of(), null, null);
        assertThat(tool.name()).isEqualTo("skill_search");
    }

    @Test
    void toolDescription() {
        var tool = new SkillSearchTool(Map.of(), null, null);
        assertThat(tool.description()).contains("Search available skills");
    }

    @Test
    void parameterType() {
        var tool = new SkillSearchTool(Map.of(), null, null);
        assertThat(tool.parameterType()).isEqualTo(SkillSearchTool.Params.class);
    }

    @Test
    void parameterSchemaHasQueryAndSkillName() {
        var tool = new SkillSearchTool(Map.of(), null, null);
        var schema = tool.parameterSchema();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties")).isNotNull();
        assertThat(schema.get("properties").get("query")).isNotNull();
        assertThat(schema.get("properties").get("skillName")).isNotNull();
    }

    @Test
    void listAllSkills() {
        var skills = Map.of("math", MATH_SKILL, "weather", WEATHER_SKILL);
        var tool = new SkillSearchTool(skills, null, null);
        var result = tool.execute("id", new SkillSearchTool.Params(null, null),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).doesNotStartWith("[ERROR]");
        assertThat(result.content().get(0).toString()).contains("math", "weather");
    }

    @Test
    void filterSkillsByQuery() {
        var skills = Map.of("math", MATH_SKILL, "weather", WEATHER_SKILL);
        var tool = new SkillSearchTool(skills, null, null);
        var result = tool.execute("id", new SkillSearchTool.Params("math", null),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).contains("math");
        assertThat(result.content().get(0).toString()).doesNotContain("weather");
    }

    @Test
    void filterSkillsByDescription() {
        var skills = Map.of("math", MATH_SKILL, "weather", WEATHER_SKILL);
        var tool = new SkillSearchTool(skills, null, null);
        var result = tool.execute("id", new SkillSearchTool.Params("lookup", null),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).contains("weather");
        assertThat(result.content().get(0).toString()).doesNotContain("math");
    }

    @Test
    void activateSkillByName() {
        var skills = Map.of("math", MATH_SKILL);
        var tool = new SkillSearchTool(skills, null, null);
        var result = tool.execute("id", new SkillSearchTool.Params(null, "math"),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).contains("Do math");
        assertThat(result.content().get(0).toString()).contains("Allowed tools: calc");
    }

    @Test
    void activateSkillNotFound() {
        var skills = Map.of("math", MATH_SKILL);
        var tool = new SkillSearchTool(skills, null, null);
        var result = tool.execute("id", new SkillSearchTool.Params(null, "nobody"),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).contains("not found");
    }

    @Test
    void listWithSkillsDirHint() {
        var tool = new SkillSearchTool(Map.of(), null, "/tmp/skills");
        var result = tool.execute("id", new SkillSearchTool.Params(null, null),
            new AtomicBoolean(false), null);
        assertThat(result.content().get(0).toString()).contains("/tmp/skills");
    }

    @Test
    void paramsRejectsBothQueryAndSkillName() {
        assertThatThrownBy(() -> new SkillSearchTool.Params("q", "s"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void paramsAllowsNeither() {
        var params = new SkillSearchTool.Params(null, null);
        assertThat(params.query()).isNull();
        assertThat(params.skillName()).isNull();
    }
}
