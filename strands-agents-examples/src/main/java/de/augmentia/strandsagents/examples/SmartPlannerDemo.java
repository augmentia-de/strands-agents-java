package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.features.context.AgentContext;
import de.augmentia.strandsagents.features.planning.CoTPlanner;
import de.augmentia.strandsagents.features.planning.PlanningAgent;
import de.augmentia.strandsagents.features.routing.LlmRouter;
import de.augmentia.strandsagents.features.secrets.FileSecretProvider;
import dev.langchain4j.model.chat.ChatModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class SmartPlannerDemo {

    public static void main(String[] args) throws IOException {
        run();
        System.out.println("=== SmartPlannerDemo PASSED ===");
    }

    public static void run() throws IOException {
        var mockModel = new MockChatModel();

        // ── 1. AgentContext: ThreadLocal session state ──
        AgentContext.SESSION.set(Map.of("userId", "demo-user", "tenant", "acme"));
        AgentContext.SESSION_ID.set("session-smart-1");
        assert AgentContext.SESSION.get().get("userId").equals("demo-user") : "context userId";
        assert AgentContext.SESSION_ID.get().equals("session-smart-1") : "context sessionId";
        System.out.println("  [Context] SESSION=" + AgentContext.SESSION.get());
        System.out.println("  [Context] SESSION_ID=" + AgentContext.SESSION_ID.get());

        // ── 2. LlmRouter: classify user intent ──
        var router = new LlmRouter(mockModel, 0.6);
        var result = router.classify("Write a poem", List.of("SIMPLE", "COMPLEX"));
        System.out.println("  [Routing] topic=" + result.topic()
            + " confidence=" + result.confidence()
            + " prompt=" + result.originalPrompt());
        assert result.topic().equals("DEFAULT") : "Mock response won't match any topic";
        assert result.confidence() == 0.0 : "confidence 0 for unknown topic";

        // ── 3. PlanningAgent + CoTPlanner: plan-then-execute ──
        var toolRegistry = ToolRegistry.builder()
            .standard()
            .build();
        var planner = new CoTPlanner(mockModel, 2, toolRegistry);
        var planningAgent = new PlanningAgent(mockModel, toolRegistry, new ToolExecutor(), planner);
        var planResult = planningAgent.executePlanned("Greet the user politely");
        System.out.println("  [Planning] result=" + planResult.finalAnswer());
        System.out.println("  [Planning] stopReason=" + planResult.stopReason());
        System.out.println("  [Planning] iterations=" + planningAgent.getIterationCount());
        System.out.println("  [Planning] revisions=" + planningAgent.getRevisionCount());
        System.out.println("  [Planning] phase=" + planningAgent.getPhase());
        assert planResult.finalAnswer() != null : "plan produced an answer";
        assert !planResult.finalAnswer().isBlank() : "plan answer not blank";

        // ── 4. FileSecretProvider: credentials for tool execution ──
        var secretsFile = Files.createTempFile("secrets-", ".json");
        Files.delete(secretsFile); // empty file can't be parsed as JSON
        try {
            var secretProvider = new FileSecretProvider(secretsFile, true);
            secretProvider.setSecret("openai", "api_key", "sk-test-123");
            secretProvider.setSecret("tools", "calculator_endpoint", "http://calc/api");

            var apiKey = secretProvider.getSecret("openai", "api_key");
            System.out.println("  [Secrets] openai/api_key=" + apiKey);
            assert "sk-test-123".equals(apiKey) : "secret read back correctly";

            var calcEndpoint = secretProvider.getSecret("tools", "calculator_endpoint");
            assert "http://calc/api".equals(calcEndpoint) : "second secret read back";

            var allOpenAi = secretProvider.getSecrets("openai");
            System.out.println("  [Secrets] openai path: " + allOpenAi);
            assert allOpenAi.size() == 1 : "one secret under openai path";

        } finally {
            Files.deleteIfExists(secretsFile);
        }

        // ── Cleanup context ──
        AgentContext.SESSION.remove();
        AgentContext.SESSION_ID.remove();
    }
}
