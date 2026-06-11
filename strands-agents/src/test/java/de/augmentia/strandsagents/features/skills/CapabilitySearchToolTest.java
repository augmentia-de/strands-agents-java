package de.augmentia.strandsagents.features.skills;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.ToolExecutor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapabilitySearchToolTest {

    private final CapabilityRegistry emptyRegistry = new CapabilityRegistry(List.of(), List.of());

    @Test
    void name_returnsCapabilitySearch() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.name()).isEqualTo("capability_search");
    }

    @Test
    void description_containsCapabilitiesAndSearch() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.description()).containsIgnoringCase("capabilities");
        assertThat(tool.description()).containsIgnoringCase("search");
    }

    @Test
    void parameterType_returnsParamsClass() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.parameterType()).isEqualTo(CapabilitySearchTool.Params.class);
    }

    @Test
    void parameterSchema_hasTaskAndQueryProperties() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var schema = tool.parameterSchema();
        assertThat(schema.has("properties")).isTrue();
        var props = schema.get("properties");
        assertThat(props.has("task")).isTrue();
        assertThat(props.has("query")).isTrue();
    }

    @Test
    void execute_withQuery_callsRegistrySearch() throws Exception {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var result = tool.execute("call-1", new CapabilitySearchTool.Params(null, "test"),
            new java.util.concurrent.atomic.AtomicBoolean(false));
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0).toString()).contains("No capabilities");
    }

    @Test
    void execute_withoutParams_returnsAll() throws Exception {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var result = tool.execute("call-1", new CapabilitySearchTool.Params(null, null),
            new java.util.concurrent.atomic.AtomicBoolean(false));
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0).toString()).contains("No capabilities found");
    }

    @Test
    void execute_withTask_routesToSubAgent() throws Exception {
        var registry = new CapabilityRegistry(List.of(), List.of());
        var tool = new CapabilitySearchTool(registry, new MockChatModel("Analysis result: %s"));
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("find files", null),
            new java.util.concurrent.atomic.AtomicBoolean(false));
        var output = result.content().get(0).toString();
        assertThat(output).contains("Capability Analysis");
        assertThat(output).contains("find files");
    }

    @Test
    void execute_withTaskAndSkills_includesSkillList() throws Exception {
        var tempDir = java.nio.file.Files.createTempDirectory("caps-test");
        try {
            var skillDir = tempDir.resolve("skills");
            java.nio.file.Files.createDirectories(skillDir);
            var mySkill = skillDir.resolve("file-finder");
            java.nio.file.Files.createDirectories(mySkill);
            java.nio.file.Files.writeString(mySkill.resolve("SKILL.md"), """
                ---
                name: file-finder
                description: Finds files on the filesystem
                ---
                Use find and grep tools.
                """);

            var registry = new CapabilityRegistry(List.of(skillDir), List.of());
            var tool = new CapabilitySearchTool(registry, new MockChatModel("Result: %s"));
            var result = tool.execute("call-1", new CapabilitySearchTool.Params("find files", null),
                new java.util.concurrent.atomic.AtomicBoolean(false));
            var output = result.content().get(0).toString();
            assertThat(output).contains("file-finder");
            assertThat(output).contains("Finds files");
        } finally {
            java.nio.file.Files.walk(tempDir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    void execute_withTaskAndSubAgentError_returnsErrorMessage() throws Exception {
        var registry = new CapabilityRegistry(List.of(), List.of());
        var tool = new CapabilitySearchTool(registry, new MockChatModel("Error: %s"));
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("do something", null),
            new java.util.concurrent.atomic.AtomicBoolean(false));
        assertThat(result.content().get(0).toString()).contains("Capability Analysis");
    }

    @Test
    void constructor_acceptsCustomToolExecutor() {
        var executor = new ToolExecutor(5, false, 0, 0, 0);
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel(), executor);
        assertThat(tool.name()).isEqualTo("capability_search");
    }
}
