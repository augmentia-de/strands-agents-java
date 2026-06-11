package de.augmentia.strandsagents.quarkus.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.skills.Skill;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentServicePromptTest {

    @Test
    void replacesToolAndSkillPlaceholders() {
        var prompt = AgentService.buildSystemPrompt(
            "Base prompt\n\nTools:\n{{tools}}\n\nSkills:\n{{skills}}",
            registry(),
            skills());

        assertThat(prompt)
            .contains("Base prompt")
            .contains("- lookup: Looks up records")
            .contains("- writer: Writes concise notes")
            .doesNotContain("{{tools}}")
            .doesNotContain("{{skills}}")
            .doesNotContain("Selected tools:")
            .doesNotContain("Selected skills:");
    }

    @Test
    void appendsSelectedCapabilitiesWhenPlaceholdersAreMissing() {
        var prompt = AgentService.buildSystemPrompt("Base prompt", registry(), skills());

        assertThat(prompt)
            .contains("Base prompt")
            .contains("Selected tools:\n- lookup: Looks up records")
            .contains("Selected skills:\n- writer: Writes concise notes");
    }

    private static ToolRegistry registry() {
        var registry = new ToolRegistry();
        registry.register(new PromptTools());
        return registry;
    }

    private static List<Skill> skills() {
        return List.of(new Skill("writer", "Writes concise notes", "", null, List.of(), Map.of(), null, null));
    }

    static class PromptTools {
        @Tool("Looks up records")
        public String lookup() {
            return "ok";
        }
    }
}
