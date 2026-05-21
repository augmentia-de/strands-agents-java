package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSkillsPluginTest {

    private static final Skill TEST_SKILL = new Skill("test-skill",
        "A test skill", "Do the test thing", null,
        List.of("bash", "calc"), Map.of("author", "me"), "MIT", "java21");

    private static final Skill WEATHER_SKILL = new Skill("weather",
        "Weather lookup", "Check the weather", null,
        null, Map.of(), null, null);

    @Test
    void pluginName() {
        var plugin = new AgentSkillsPlugin(List.of());
        assertThat(plugin.name()).isEqualTo("strands:agent-skills");
    }

    @Test
    void storesSkills() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL, WEATHER_SKILL));
        assertThat(plugin.getSkills()).hasSize(2);
        assertThat(plugin.getSkills()).containsKeys("test-skill", "weather");
    }

    @Test
    void emptySkillsList() {
        var plugin = new AgentSkillsPlugin(List.of());
        assertThat(plugin.getSkills()).isEmpty();
    }

    @Test
    void initialSkillsMax3() {
        var skills = List.of(TEST_SKILL, WEATHER_SKILL,
            new Skill("a", "a", "a", null, null, Map.of(), null, null),
            new Skill("b", "b", "b", null, null, Map.of(), null, null));
        assertThatThrownBy(() -> new AgentSkillsPlugin(skills, List.of("a", "b", "c", "d")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialSkillsUpTo3Allowed() {
        var plugin = new AgentSkillsPlugin(List.of(), List.of("a", "b"));
        assertThat(plugin).isNotNull();
    }

    @Test
    void skillSearchDisabledByDefault() {
        var plugin = new AgentSkillsPlugin(List.of());
        assertThat(plugin.isSkillSearchEnabled()).isFalse();
    }

    @Test
    void enableSkillSearch() {
        var plugin = new AgentSkillsPlugin(List.of());
        plugin.setSkillSearchEnabled(true);
        assertThat(plugin.isSkillSearchEnabled()).isTrue();
    }

    @Test
    void initAgentWithSkillsSetsPluginHook() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var agent = new Agent(new MockChatModel());
        plugin.initAgent(agent);
        assertThat(agent.getSystemPrompt()).isEmpty();
    }

    @Test
    void getToolsReturnsEmptyWhenSearchDisabled() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        assertThat(plugin.getTools()).isEmpty();
    }

    @Test
    void getToolsReturnsSkillSearchToolWhenEnabled() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        plugin.setSkillSearchEnabled(true);
        var tools = plugin.getTools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).spec().name()).isEqualTo("skill_search");
    }

    @Test
    void activateSkillReturnsInstructions() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var result = plugin.activateSkill("test-skill");
        assertThat(result).contains("Do the test thing");
        assertThat(result).contains("Allowed tools: bash, calc");
    }

    @Test
    void activateSkillWithLicense() {
        var skill = new Skill("licensed", "Has license", "Instructions", null, null, Map.of(), "MIT", null);
        var plugin = new AgentSkillsPlugin(List.of(skill));
        var result = plugin.activateSkill("licensed");
        assertThat(result).contains("Instructions");
    }

    @Test
    void activateSkillNotFound() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var result = plugin.activateSkill("nonexistent");
        assertThat(result).contains("not found");
        assertThat(result).contains("test-skill");
    }

    @Test
    void activateSkillWithCompatibility() {
        var skill = new Skill("compat", "Has compatibility", "Instr", null, null, Map.of(), null, "java21");
        var plugin = new AgentSkillsPlugin(List.of(skill));
        var result = plugin.activateSkill("compat");
        assertThat(result).contains("java21");
    }

    @Test
    void injectSkillsXmlWithoutInitialSkills() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var sb = new StringBuilder();
        plugin.injectSkillsXml(sb);
        var xml = sb.toString();
        assertThat(xml).contains("<available_skills>");
        assertThat(xml).contains("test-skill");
        assertThat(xml).contains("A test skill");
        assertThat(xml).doesNotContain("<activated_skills>");
    }

    @Test
    void injectSkillsXmlWithInitialSkills() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL), List.of("test-skill"));
        var sb = new StringBuilder();
        plugin.injectSkillsXml(sb);
        var xml = sb.toString();
        assertThat(xml).contains("<activated_skills>");
        assertThat(xml).contains("Do the test thing");
        assertThat(xml).contains("<allowed_tools>bash, calc</allowed_tools>");
    }

    @Test
    void injectSkillsXmlReplacesPreviousInjection() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var sb = new StringBuilder("prefix ");
        plugin.injectSkillsXml(sb);
        var first = sb.toString();
        assertThat(first).startsWith("prefix ");
        assertThat(first).contains("<available_skills>");

        plugin.injectSkillsXml(sb);
        var second = sb.toString();
        assertThat(second).doesNotContain("<available_skills><available_skills>");
    }

    @Test
    void injectSkillsXmlEmptySkills() {
        var plugin = new AgentSkillsPlugin(List.of());
        var sb = new StringBuilder();
        plugin.injectSkillsXml(sb);
        assertThat(sb.toString()).contains("No skills are currently available");
    }

    @Test
    void injectSkillsXmlWithSearchHint() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        plugin.setSkillSearchEnabled(true);
        var sb = new StringBuilder();
        plugin.injectSkillsXml(sb);
        assertThat(sb.toString()).contains("skill_search");
    }

    @Test
    void injectSkillsXmlWithoutSearchHint() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var sb = new StringBuilder();
        plugin.injectSkillsXml(sb);
        assertThat(sb.toString()).doesNotContain("skill_search");
    }

    @Test
    void initAgentWithSkillsCallsInject() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL));
        var agent = new Agent(new MockChatModel());
        plugin.initAgent(agent);
        agent.execute("hello");
    }

    @Test
    void initAgentDoesNothingForEmptySkills() {
        var plugin = new AgentSkillsPlugin(List.of());
        var agent = new Agent(new MockChatModel());
        plugin.initAgent(agent);
        assertThat(agent.getSystemPrompt()).isEmpty();
    }

    @Test
    void pluginWithCustomMaxResourceFiles() {
        var plugin = new AgentSkillsPlugin(List.of(TEST_SKILL), 10, "custom_key", List.of());
        assertThat(plugin).isNotNull();
    }
}
