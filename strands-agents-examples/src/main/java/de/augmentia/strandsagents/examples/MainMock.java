package de.augmentia.strandsagents.examples;


import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.subagent.SubAgentTool;
import de.augmentia.strandsagents.features.swarm.SwarmOrchestrator;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.model.event.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;

public class MainMock {

    public static void main(String[] args) {
        System.out.println("=== Strands Agent (Mock) — Demo ===");
        System.out.println();

        demoBasics();
        demoAgentTool();
        demoSwarmOrchestrator();
        demoAgentConfig();
    }

    static void demoBasics() {
        System.out.println("─── 1. Basis: Events + Tools + Sessions ───");

        var registry = ToolRegistry.builder()
            .with("de.augmentia.strandsagents.features.tools.CalculatorTool")
            .build();
        var model = new DemoMockModel();
        var agent = new Agent(model, registry, new ToolExecutor());
        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e -> System.out.println("  [EVENT] Started");
                case ModelRequestedEvent e -> System.out.println("  [EVENT] LLM-Call (" + e.promptHistory().size() + " messages)");
                case ToolExecutionStartedEvent e -> System.out.println("  [EVENT] Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e -> System.out.println("  [EVENT] Result: " + e.result().toolName() + " -> " + e.result().result());
                case BeforeInvocationEvent e -> {}
                case AfterInvocationEvent e -> {}
                case AgentStateChangedEvent e -> System.out.println("  [EVENT] State: " + e.previousPhase() + " -> " + e.currentPhase());
                case TokenEvent e -> {}
                case AgentFinishedEvent e -> System.out.println("  [EVENT] Finished");
            }
        });

        var result = agent.execute("Hello world");
        System.out.println("  Agent: " + result.finalAnswer());
        System.out.println("  StopReason: " + result.stopReason());
        System.out.println("  Tool-Calls: " + result.metrics().toolCallsCount()
            + ", " + result.metrics().durationMs() + " ms");
        System.out.println();
    }

    static void demoAgentTool() {
        System.out.println("─── 2. A2A: Agent ruft Sub-Agent als Tool auf ───");

        var subModel = new DemoMockModel();
        var subAgent = new Agent(subModel);
        subAgent.setEventListener(e -> {});

        var agentTool = new SubAgentTool(subAgent, "research",
            "Performs searches in a knowledge database");

        System.out.println("  AgentTool-Typ: " + agentTool.getClass().getSimpleName());
        System.out.println("  @Tool-Methode: execute(String prompt)");
        System.out.println("  Rekursionstiefe: max " + SubAgentTool.MAX_RECURSION_DEPTH);

        var registry = ToolRegistry.builder()
            .with(agentTool)
            .build();
        System.out.println("  Registrierte Tools: " + registry.getToolNames());

        var parentModel = new DemoMockModel();
        var parentAgent = new Agent(parentModel, registry, new ToolExecutor());
        var result = parentAgent.execute("Research the weather");
        System.out.println("  Parent-Agent: " + result.finalAnswer());
        System.out.println();
    }

    static void demoSwarmOrchestrator() {
        System.out.println("─── 3. Swarm: Orchestrator routes by Topic ───");

        var weatherAgent = new Agent(new DemoMockModel("Weather report: %s"));
        var mathAgent = new Agent(new DemoMockModel("Math result: %s"));

        var defaultAgent = new Agent(new DemoMockModel("General: %s"));

        var orchestrator = new SwarmOrchestrator(Map.of(
            "weather", weatherAgent,
            "math", mathAgent
        ), defaultAgent);

        System.out.println("  Routen: " + orchestrator.getRoutes().stream()
            .map(r -> r.topic()).toList());

        for (var prompt : List.of(
            "What will the weather be tomorrow?",
            "Solve this math problem: 5 + 3",
            "Hello, how are you?"
        )) {
            var result = orchestrator.execute(prompt);
            System.out.println("  Prompt: \"" + prompt + "\"");
            System.out.println("  → " + result.finalAnswer());
        }
        System.out.println();
    }

    static void demoAgentConfig() {
        System.out.println("─── 4. Config: AgentConfig + ToolRegistry.Builder ───");

        var config = AgentConfig.builder()
            .name("research-agent")
            .modelName("openai/gpt-4o")
            .systemPrompt("You are a research agent.")
            .toolRegistry(ToolRegistry.builder()
                .standard()
                .include("bash", "read", "ls")
                .build())
            .maxIterations(15)
            .build();

        System.out.println("  Name: " + config.name());
        System.out.println("  Model: " + config.modelName());
        System.out.println("  SystemPrompt: " + config.systemPrompt());
        System.out.println("  Tools: " + config.toolRegistry().getToolNames());
        System.out.println("  MaxIterations: " + config.maxIterations());

        var agent = config.createAgent(new DemoMockModel());
        System.out.println("  Agent created via config.createAgent(model)");

        var result = agent.execute("Hallo");
        System.out.println("  Agent: " + result.finalAnswer());
        System.out.println();
    }

    static class DemoMockModel implements ChatModel {
        private final String template;
        DemoMockModel() { this("Mock response: %s"); }
        DemoMockModel(String template) { this.template = template; }

        @Override
        public ChatResponse chat(ChatRequest request) {
            var messages = request.messages();
            if (messages.isEmpty()) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .tokenUsage(new TokenUsage(0, 0))
                    .finishReason(FinishReason.STOP)
                    .build();
            }
            var last = messages.get(messages.size() - 1);
            var text = last instanceof UserMessage um
                ? um.singleText() : last.toString();
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(template.formatted(text)))
                .tokenUsage(new TokenUsage(10, text.length()))
                .finishReason(FinishReason.STOP)
                .build();
        }
    }
}
