package de.augmentia.strandsagents.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.a2a.SubAgentExecutor;
import de.augmentia.strandsagents.core.agent.a2a.SubAgentTool;

import de.augmentia.strandsagents.core.agent.routing.LlmRouter;
import de.augmentia.strandsagents.core.agent.swarm.SwarmOrchestrator;
import org.junit.jupiter.api.Test;

class EnhancedMultiAgentTest {

    // --- LlmRouter Tests ---

    @Test
    void llmRouterShouldClassifyPrompt() {
        var router = new LlmRouter(new MockChatModel("wetter"));
        var result = router.classify("Wie wird das Wetter?", List.of("wetter", "mathe"));

        assertThat(result.topic()).isEqualTo("wetter");
        assertThat(result.confidence()).isGreaterThan(0);
        assertThat(result.originalPrompt()).isEqualTo("Wie wird das Wetter?");
    }

    @Test
    void llmRouterShouldReturnDefaultForUnknownTopic() {
        var router = new LlmRouter(new MockChatModel("unknown"));
        var result = router.classify("Hallo", List.of("wetter", "mathe"));

        assertThat(result.topic()).isEqualTo("DEFAULT");
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void llmRouterShouldReturnDefaultOnModelError() {
        var failingModel = new MockChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                throw new RuntimeException("API error");
            }
        };
        var router = new LlmRouter(failingModel);
        var result = router.classify("Test", List.of("topic1"));

        assertThat(result.topic()).isEqualTo("DEFAULT");
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void llmRouterShouldIgnoreCase() {
        var router = new LlmRouter(new MockChatModel("WETTER"));
        var result = router.classify("Wetter morgen", List.of("wetter", "mathe"));

        assertThat(result.topic()).isEqualTo("wetter");
    }

    // --- SubAgentExecutor Tests ---

    @Test
    void a2aExecutorShouldCallAgent() {
        var agent = new Agent(new MockChatModel("Antwort: %s"));
        var executor = new SubAgentExecutor();
        var result = executor.call(agent, "Hallo");

        assertThat(result.agentName()).isEqualTo("Agent");
        assertThat(result.prompt()).isEqualTo("Hallo");
        assertThat(result.result()).isNotEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void a2aExecutorShouldHandleTimeout() {
        var slowAgent = new Agent(new MockChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                try { Thread.sleep(10000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return super.chat(request);
            }
        });
        var executor = new SubAgentExecutor(1, 0, Map.of());
        var result = executor.call(slowAgent, "Hallo");

        assertThat(result.result()).contains("A2A-Fehler");
    }

    @Test
    void a2aExecutorShouldRetryOnFailure() {
        var counter = new AtomicInteger();
        var failingAgent = new Agent(new MockChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                if (counter.incrementAndGet() < 2) {
                    throw new RuntimeException("temporärer Fehler");
                }
                return super.chat(request);
            }
        });
        var executor = new SubAgentExecutor(60, 2, Map.of());
        var result = executor.call(failingAgent, "Hallo");

        assertThat(result.result()).isNotEmpty();
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void a2aExecutorShouldPropagateMetadata() {
        var agent = new Agent(new MockChatModel());
        var executor = new SubAgentExecutor(60, 1, Map.of("traceId", "abc-123", "userId", "user-1"));
        var result = executor.call(agent, "Hallo");

        assertThat(result.metadata()).containsEntry("traceId", "abc-123");
        assertThat(result.metadata()).containsEntry("userId", "user-1");
    }

    @Test
    void a2aExecutorAsyncShouldComplete() {
        var agent = new Agent(new MockChatModel());
        var executor = new SubAgentExecutor();

        var future = executor.callAsync(agent, "Hallo");
        var result = future.join();

        assertThat(result.result()).isNotEmpty();
    }

    @Test
    void a2aExecutorShouldAcceptCustomAgentName() {
        var agent = new Agent(new MockChatModel());
        var executor = new SubAgentExecutor();
        var result = executor.call(agent, "Test", "mein-agent");

        assertThat(result.agentName()).isEqualTo("mein-agent");
    }

    // --- SubAgentTool with SubAgentExecutor Tests ---

    @Test
    void agentToolShouldWorkWithSubAgentExecutor() {
        var subAgent = new Agent(new MockChatModel("Sub: %s"));
        var executor = new SubAgentExecutor();
        var tool = new SubAgentTool(subAgent, "recherche", "Recherchiert", executor);

        var result = tool.execute("Thema");
        assertThat(result).contains("Sub");
    }

    @Test
    void agentToolShouldPropagateMetadataViaExecutor() {
        var subAgent = new Agent(new MockChatModel("Sub: %s"));
        var executor = new SubAgentExecutor(60, 1, Map.of("traceId", "trace-xyz"));
        var tool = new SubAgentTool(subAgent, "helper", "Hilft", executor);

        var result = tool.execute("Bitte helfen");
        assertThat(result).contains("Sub");
    }

    @Test
    void agentToolShouldStillRespectRecursionDepth() {
        var inner = new Agent(new MockChatModel("Inner: %s"));
        var tool = new SubAgentTool(inner, "nested");
        var result = tool.execute("Ebene 1");
        assertThat(result).doesNotContain("Rekursionstiefe");
    }

    // --- SwarmOrchestrator with LlmRouter Tests ---

    @Test
    void swarmOrchestratorShouldUseLlmRouter() {
        var weatherAgent = new Agent(new MockChatModel("Wetter: %s"));
        var mathAgent = new Agent(new MockChatModel("Mathe: %s"));
        var defaultAgent = new Agent(new MockChatModel("Default: %s"));

        var router = new LlmRouter(new MockChatModel("wetter"));
        var orchestrator = new SwarmOrchestrator(router,
            Map.of("wetter", weatherAgent, "mathe", mathAgent),
            defaultAgent);

        var result = orchestrator.execute("Wie wird das Wetter?");
        assertThat(result.finalAnswer()).contains("Wetter");
    }

    @Test
    void swarmOrchestratorShouldFallbackToKeywordMatching() {
        var weatherAgent = new Agent(new MockChatModel("Wetter: %s"));
        var defaultAgent = new Agent(new MockChatModel("Default: %s"));

        var router = new LlmRouter(new MockChatModel("DEFAULT"));
        var orchestrator = new SwarmOrchestrator(router,
            List.of(new SwarmOrchestrator.Route("wetter", weatherAgent)),
            defaultAgent);

        var result = orchestrator.execute("Wetter morgen");
        assertThat(result.finalAnswer()).contains("Wetter");
    }

    @Test
    void swarmOrchestratorShouldFallbackToDefaultWhenConfidenceLow() {
        var weatherAgent = new Agent(new MockChatModel("Wetter: %s"));
        var defaultAgent = new Agent(new MockChatModel("Default: %s"));

        var router = new LlmRouter(new MockChatModel("wetter"), 0.95);
        var orchestrator = new SwarmOrchestrator(router,
            List.of(new SwarmOrchestrator.Route("wetter", weatherAgent)),
            defaultAgent);

        var result = orchestrator.execute("Irgendwas");
        assertThat(result.finalAnswer()).contains("Default");
    }

    @Test
    void swarmOrchestratorShouldWorkWithoutRouter() {
        var weatherAgent = new Agent(new MockChatModel("Wetter: %s"));
        var defaultAgent = new Agent(new MockChatModel("Default: %s"));

        var orchestrator = new SwarmOrchestrator(
            Map.of("wetter", weatherAgent), defaultAgent);

        var result = orchestrator.execute("Wetter morgen");
        assertThat(result.finalAnswer()).contains("Wetter");
    }

    @Test
    void swarmOrchestratorShouldProvideRouter() {
        var router = new LlmRouter(new MockChatModel("test"));
        var agent = new Agent(new MockChatModel());
        var orchestrator = new SwarmOrchestrator(router,
            List.of(new SwarmOrchestrator.Route("test", agent)), agent);

        assertThat(orchestrator.getRouter()).isSameAs(router);
    }

    // --- Integration Tests ---

    @Test
    void fullOrchestrationWithSubAgentExecutor() {
        var subAgent = new Agent(new MockChatModel("Sub: %s"));
        var a2aExecutor = new SubAgentExecutor(60, 1, Map.of("traceId", "t1"));
        var agentTool = new SubAgentTool(subAgent, "sub-agent", "Hilfs-Agent", a2aExecutor);

        var registry = new ToolRegistry();
        registry.register(agentTool);

        var mainAgent = new Agent(new MockChatModel(), registry, new ToolExecutor());
        var result = mainAgent.execute("Hilf mir bitte");

        assertThat(result.finalAnswer()).isNotEmpty();
    }
}
