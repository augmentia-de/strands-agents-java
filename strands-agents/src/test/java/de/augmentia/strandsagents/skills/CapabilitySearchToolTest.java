package de.augmentia.strandsagents.skills;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.skills.CapabilityEmbeddingService;
import de.augmentia.strandsagents.skills.CapabilityRegistry;
import de.augmentia.strandsagents.skills.CapabilitySearchTool;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CapabilitySearchToolTest {

    private final CapabilityRegistry emptyRegistry = new CapabilityRegistry(List.of(), List.of());

    @Test
    void name_returnsCapabilitySearch() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.name()).isEqualTo("capability_search");
    }

    @Test
    void description_containsCapabilitiesAndAnalyze() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.description()).containsIgnoringCase("capabilities");
        assertThat(tool.description()).containsIgnoringCase("analyze");
    }

    @Test
    void parameterType_returnsParamsClass() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        assertThat(tool.parameterType()).isEqualTo(CapabilitySearchTool.Params.class);
    }

    @Test
    void parameterSchema_hasTaskProperty() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var schema = tool.parameterSchema();
        assertThat(schema.has("properties")).isTrue();
        var props = schema.get("properties");
        assertThat(props.has("task")).isTrue();
        assertThat(props.has("query")).isFalse();
    }

    @Test
    void parameterSchema_taskIsRequired() {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var schema = tool.parameterSchema();
        assertThat(schema.has("required")).isTrue();
        assertThat(schema.get("required")).hasSize(1);
        assertThat(schema.get("required").get(0).asText()).isEqualTo("task");
    }

    @Test
    void execute_withoutTask_returnsHelpMessage() throws Exception {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var result = tool.execute("call-1", new CapabilitySearchTool.Params(null),
            new AtomicBoolean(false));
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0).toString()).containsIgnoringCase("provide a 'task'");
    }

    @Test
    void execute_withBlankTask_returnsHelpMessage() throws Exception {
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel());
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("  "),
            new AtomicBoolean(false));
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().get(0).toString()).containsIgnoringCase("provide a 'task'");
    }

    @Test
    void execute_withSingleVectorMatch_returnsViaSubAgent() throws Exception {
        var writeCap = new CapabilityRegistry.Capability("write", "Write content", "default",
            CapabilityRegistry.CapabilityType.DEFAULT);
        var embeddingModel = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return Response.from(segments.stream()
                    .map(s -> new Embedding(new float[]{1, 0, 0}))
                    .toList());
            }
        };
        var embeddingService = new CapabilityEmbeddingService(embeddingModel, List.of(writeCap), 0.5);
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel(),
            new DefaultToolExecutor(), embeddingService);
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("write"),
            new AtomicBoolean(false));
        var output = result.content().get(0).toString();
        assertThat(output).contains("\"task\"");
        assertThat(output).contains("write");
    }

    @Test
    void execute_withVectorSearchNoMatch_fallsBackToSubAgent() throws Exception {
        var writeCap = new CapabilityRegistry.Capability("write", "Write content", "default",
            CapabilityRegistry.CapabilityType.DEFAULT);
        var embeddingModel = new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return Response.from(segments.stream()
                    .map(s -> new Embedding(new float[]{0, 0, 0}))
                    .toList());
            }
        };
        var embeddingService = new CapabilityEmbeddingService(embeddingModel, List.of(writeCap), 0.99);
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel("LLM: %s"),
            new DefaultToolExecutor(), embeddingService);
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("write"),
            new AtomicBoolean(false));
        var output = result.content().get(0).toString();
        assertThat(output).contains("analysis");
        assertThat(output).contains("LLM");
    }

    @Test
    void execute_withTask_routesToSubAgent() throws Exception {
        var registry = new CapabilityRegistry(List.of(), List.of());
        var tool = new CapabilitySearchTool(registry, new MockChatModel("Analysis result: %s"));
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("find files"),
            new AtomicBoolean(false));
        var output = result.content().get(0).toString();
        assertThat(output).contains("\"task\"");
        assertThat(output).contains("find files");
        assertThat(output).contains("instruction");
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
            var result = tool.execute("call-1", new CapabilitySearchTool.Params("find files"),
                new AtomicBoolean(false));
            var output = result.content().get(0).toString();
            assertThat(output).contains("\"task\"");
            assertThat(output).contains("find files");
            assertThat(output).contains("Result");
            assertThat(output).contains("Proceed with the matching tools");
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
        var result = tool.execute("call-1", new CapabilitySearchTool.Params("do something"),
            new AtomicBoolean(false));
        assertThat(result.content().get(0).toString()).contains("\"task\"");
    }

    @Test
    void constructor_acceptsCustomToolExecutor() {
        var executor = new DefaultToolExecutor();
        var tool = new CapabilitySearchTool(emptyRegistry, new MockChatModel(), executor);
        assertThat(tool.name()).isEqualTo("capability_search");
    }
}
