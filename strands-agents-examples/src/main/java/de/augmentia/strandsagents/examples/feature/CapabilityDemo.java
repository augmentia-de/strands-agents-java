package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.config.LlmConfig;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;

import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.skills.AgentSkillsPlugin;
import de.augmentia.strandsagents.skills.CapabilityEmbeddingService;
import de.augmentia.strandsagents.skills.CapabilityRegistry;
import de.augmentia.strandsagents.skills.CapabilitySearchTool;
import de.augmentia.strandsagents.skills.SkillActivationHook;
import de.augmentia.strandsagents.skills.SkillParser;
import de.augmentia.strandsagents.tools.ToolActivator;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import java.io.IOException;
import java.nio.file.Path;

public class CapabilityDemo {

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

        // ---- Load real project skills ----
        var skillsDir = Path.of("skills");
        var skills = SkillParser.fromDirectory(skillsDir);
        System.out.println("Loaded skills: " + skills.stream().map(s -> s.name() + " (" + s.description() + ")").toList());

        // ---- Hooks ----
        var skillActivationHook = new SkillActivationHook(skills);
        var hooks = new HookRegistry();
        hooks.register(skillActivationHook);

        // ---- Capability registry ----
        var capRegistry = CapabilityRegistry.builder()
            .skillDir(skillsDir)
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
            embeddingModel, capRegistry.discoverAll(), 0.5);

        // ---- Skills plugin (injects available skills as XML) ----
        var skillsPlugin = new AgentSkillsPlugin(skills);
        skillsPlugin.setSkillSearchEnabled(false);

        // ---- Tool registry ----
        var toolRegistry = new ToolRegistry();
        Path ws = Path.of(".").toAbsolutePath();
        toolRegistry.register(new CapabilitySearchTool(capRegistry, model, new DefaultToolExecutor(), embeddingService));
        toolRegistry.register(new ToolActivator(toolRegistry, ws));

        // ---- Agent with minimal prompt (no skill name hints) ----
        var agent = Agent.builder()
            .model(model)
            .toolRegistry(toolRegistry)
            .plugins(List.of(skillsPlugin))
            .hookRegistry(hooks)
            .systemPrompt("""
                You are in a sandbox with tools to write and read files.

                Available tools:
                - capability_search: discover skills and tools that match your task
                - tool_activator: add or remove tools at runtime

                Always start by discovering what capabilities are available for your task.
                After discovering, use the tools to complete the task — do not just describe the solution.
                """)
            .build();

        var prompt = "Write a Hello.java file that prints \"Hello, world!\" using best Java coding practices.";

        System.out.println("=== Capability Demo (independent skill discovery) ===\n");
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
    }
}
