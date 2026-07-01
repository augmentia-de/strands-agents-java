package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.config.LlmConfig;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.pipeline.AgentHook;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookRegistry;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.skills.CapabilityRegistry;
import de.augmentia.strandsagents.features.skills.CapabilitySearchTool;
import de.augmentia.strandsagents.features.tools.ReadTool;
import de.augmentia.strandsagents.features.tools.WriteTool;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CachingDemo {

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
        Path tmpDir = Files.createTempDirectory("cap-demo-");
        tmpDir.toFile().deleteOnExit();

        Path writeSkillDir = tmpDir.resolve("write-files");
        Files.createDirectories(writeSkillDir);
        writeSkillDir.toFile().deleteOnExit();
        Files.writeString(writeSkillDir.resolve("SKILL.md"), """
            ---
            name: write-files
            description: Write files to the workspace (tool: write)
            ---
            Instructions for writing files to the workspace.
            Use the 'write' tool to create or overwrite files.
            """).toFile().deleteOnExit();

        Path readSkillDir = tmpDir.resolve("read-files");
        Files.createDirectories(readSkillDir);
        readSkillDir.toFile().deleteOnExit();
        Files.writeString(readSkillDir.resolve("SKILL.md"), """
            ---
            name: read-files
            description: Read files from the workspace (tool: read)
            ---
            Instructions for reading files from the workspace.
            Use the 'read' tool to read file contents.
            """).toFile().deleteOnExit();

        // ---- Capability registry ----
        var capRegistry = CapabilityRegistry.builder()
            .skillDir(tmpDir)
            .includeStandardTools(true)
            .build();

        // ---- Tool registry (starts with only capability_search) ----
        var toolRegistry = new ToolRegistry();
        toolRegistry.register(new CapabilitySearchTool(capRegistry, model));

        // ---- Hooks ----
        var hooks = new HookRegistry();
        hooks.register(new CacheLoggingHook());

        // ---- Agent ----
        Path ws = Path.of(".").toAbsolutePath();
        var agent = Agent.builder()
            .model(model)
                .conversationManager(new SlidingWindowConversationManager(10))
            .toolRegistry(toolRegistry)
            .hookRegistry(hooks)
            .systemPrompt("""
                You are in a dynamic tool sandbox. Your available tools may change between steps.
                Use whatever tools you have available to accomplish each task.
                """)
            .build();

        System.out.println("=== Caching Demo: 3-Execute Discover → Write → Read ===\n");

        // ── Execute 1: Discover ────────────────────────────────────────
        String prompt1 = "Use capability_search to discover what capabilities "
            + "are available for writing and reading files.";
        printRun(agent, 1, prompt1);

        // ── Programmatic: add write tool ───────────────────────────────
        agent.addTool(new WriteTool(ws));
        System.out.println("  [Host] Added 'write' tool\n");

        // ── Execute 2: Write ───────────────────────────────────────────
        String prompt2 = "Use the write tool to write a file called "
            + "'hello.txt' with the content 'Hello, world!'";
        printRun(agent, 2, prompt2);

        // ── Programmatic: swap write → read ────────────────────────────
        agent.removeTool("write");
        agent.addTool(new ReadTool(ws));
        System.out.println("  [Host] Swapped 'write' → 'read'\n");

        // ── Execute 3: Read ────────────────────────────────────────────
        String prompt3 = "Use the read tool to read 'hello.txt' and report its contents.";
        printRun(agent, 3, prompt3);

        // ── Cleanup ────────────────────────────────────────────────────
        try (var walk = Files.walk(tmpDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
        System.out.println("=== Done ===");
    }

    private static void printRun(Agent agent, int number, String prompt) {
        System.out.println("--- Execute " + number + " ---");
        System.out.println("  Prompt: " + prompt);
        var result = agent.execute(prompt);
        System.out.println("  Agent:  " + result.finalAnswer());
        System.out.println("  Stop:   " + result.stopReason());
        System.out.println("  Tools:  " + result.metrics().toolCallsCount() + " calls");
        System.out.println("  Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out\n");
    }

    public static class CacheLoggingHook implements AgentHook {
        @Override
        public String name() {
            return "cache-logger";
        }


        @Override
        public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String toolResult) {
            var response = ctx.toolName();
            if (response == null) {
                return new HookResult.Continue();
            }
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse) {
            var response = ctx.chatResponse();
            if (response == null) {
                return new HookResult.Continue();
            }

            var metadata = response.metadata();
            if (!(metadata instanceof OpenAiChatResponseMetadata oaiMeta)) {
                return new HookResult.Continue();
            }

            var tokenUsage = oaiMeta.tokenUsage();
            var modelName = oaiMeta.modelName();
            var inputTokens = tokenUsage != null ? tokenUsage.inputTokenCount() : 0;
            var outputTokens = tokenUsage != null ? tokenUsage.outputTokenCount() : 0;

            System.out.println("  [Cache] Model:         " + modelName);
            System.out.println("  [Cache] ID:            " + oaiMeta.id());

            if (tokenUsage instanceof OpenAiTokenUsage oaiUsage) {
                var details = oaiUsage.inputTokensDetails();
                var cached = details != null ? details.cachedTokens() : null;
                if (cached != null && cached > 0) {
                    var pct = 100.0 * cached / Math.max(inputTokens, 1);
                    System.out.println("  [Cache] Input tokens:  " + inputTokens + " (cached: " + cached + ")");
                    System.out.printf("  [Cache] Cache hit:     %.1f%%%n", pct);
                } else {
                    System.out.println("  [Cache] Input tokens:  " + inputTokens + " (no cache)");
                }
                var outDetails = oaiUsage.outputTokensDetails();
                var reasoning = outDetails != null ? outDetails.reasoningTokens() : null;
                if (reasoning != null && reasoning > 0) {
                    System.out.println("  [Cache] Output tokens: " + outputTokens + " (reasoning: " + reasoning + ")");
                } else {
                    System.out.println("  [Cache] Output tokens: " + outputTokens);
                }
            } else {
                System.out.println("  [Cache] Input tokens:  " + inputTokens);
                System.out.println("  [Cache] Output tokens: " + outputTokens);
                System.out.println("  [Cache] (token details not available for this model)");
            }
            return new HookResult.Continue();
        }
    }
}
