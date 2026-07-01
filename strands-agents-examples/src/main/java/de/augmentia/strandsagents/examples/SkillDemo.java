package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.config.LlmConfig;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.pipeline.HookRegistry;
import de.augmentia.strandsagents.features.skills.*;
import de.augmentia.strandsagents.features.tools.ToolActivator;
import de.augmentia.strandsagents.examples.feature.CachingDemo;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SkillDemo {

    public static void main(String[] args) throws IOException {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Error: OPENAI_API_KEY not set.");
            System.exit(1);
        }

        LlmConfig config = LlmConfig.fromEnv();

        // ---- Model ----
        var model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(config.modelName())
                .baseUrl(config.baseUrl())
                .temperature(0.0)
                .seed(42)
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .build();

        // ---- Temp SKILL.md files for capability discovery ----
        Path tmpDir = Path.of("skills","");
        tmpDir.toFile().deleteOnExit();

        Path writeSkillDir = tmpDir.resolve("java-coding-standards");
        // ---- Skills ----
        var skills = SkillParser.fromDirectory(tmpDir);

        // ---- Hooks ----
        var skillActivationHook = new SkillActivationHook(skills);
        var hooks = new HookRegistry();
        hooks.register(new CachingDemo.CacheLoggingHook());
        hooks.register(skillActivationHook);

        // ---- Capability registry ----
        var capRegistry = CapabilityRegistry.builder()
            .skillDir(tmpDir)
            .includeStandardTools(true)
            .build();

        // ---- Embedding-based fast path ----
        var embeddingModelName = System.getenv("EMBEDDING_MODEL");
        if (embeddingModelName == null || embeddingModelName.isBlank()) {
            embeddingModelName = "text-embedding-3-small";
        }
        var embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName(embeddingModelName)
            .baseUrl(config.baseUrl())
            .build();
        var embeddingService = new CapabilityEmbeddingService(
            embeddingModel, capRegistry.discoverAll(), 0.75);

        // ---- Tool registry ----
        var toolRegistry = new ToolRegistry();
        Path ws = Path.of(".").toAbsolutePath();
        toolRegistry.register(new CapabilitySearchTool(capRegistry, model, new ToolExecutor(), embeddingService));
        toolRegistry.register(new ToolActivator(toolRegistry, ws));

        // ---- Skills plugin (injects skill instructions into system prompt) ----
        var skillsPlugin = new AgentSkillsPlugin(skills);
        skillsPlugin.setSkillSearchEnabled(false);

        // ---- Agent ----
        var agent = Agent.builder()
            .model(model)
            .toolRegistry(toolRegistry)
            .plugins(List.of(skillsPlugin))
                .hookRegistry(hooks)
            .systemPrompt("""
                You are in a capability-demo sandbox.

                INITIAL TOOLS:
                - capability_search: discover available capabilities (skills/tools)
                - tool_activator: activate/deactivate tools (action="add"|"remove", tool="write"|"read")

                Follow these steps IN ORDER:

                1. DISCOVER — Use capability_search to find write-file and read-file capabilities.
                   Look at the description — each mentions (tool: write) or (tool: read).

                2. ACTIVATE — Use tool_activator(action="add", tool="write") to make the write tool available.

                3. CREATE — Write a file called "hello.txt" with content "Hello, world!".

                4. SWAP — Use tool_activator to remove "write" and add "read".

                5. VERIFY — Read "hello.txt" to confirm the content was written correctly.

                Report what you found and did at each step.
                """)
            .build();

        var prompt = "Discover capabilities, write a Java Hello world Source file, and verify the file content.";

        System.out.println("=== Capability Demo (in-session tool hot-swapping) ===\n");
        System.out.println("Prompt: " + prompt + "\n");

        long start = System.nanoTime();
        var result = agent.execute(prompt);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.println("\n=== Result ===");
        System.out.println("  Agent: " + result.finalAnswer());
        System.out.println("  Stop:  " + result.stopReason());
        System.out.println("  Time:  " + elapsedMs + " ms");
        System.out.println("  Tools: " + result.metrics().toolCallsCount() + " calls");
        System.out.println("  Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out");

        // Cleanup
        try (var walk = Files.walk(tmpDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }
}
