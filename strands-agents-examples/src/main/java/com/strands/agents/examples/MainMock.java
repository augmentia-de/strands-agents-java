package com.strands.agents.examples;

import com.strands.agents.core.*;
import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.event.*;
import com.strands.agents.core.tools.CalculatorTool;
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
        System.out.println("=== Strands Agent (Mock) — Step 5: Multi-Agent Demo ===");
        System.out.println();

        demoBasics();
        demoAgentTool();
        demoSwarmOrchestrator();
        demoAgentConfig();
    }

    static void demoBasics() {
        System.out.println("─── 1. Basis: Events + Tools + Sessions ───");

        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var model = new DemoMockModel();
        var agent = new StrandsAgent(model, registry, new ToolExecutor());
        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e -> System.out.println("  [EVENT] Gestartet");
                case ModelRequestedEvent e -> System.out.println("  [EVENT] LLM-Call (" + e.promptHistory().size() + " Nachrichten)");
                case ToolExecutionStartedEvent e -> System.out.println("  [EVENT] Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e -> System.out.println("  [EVENT] Result: " + e.result().toolName() + " → " + e.result().result());
                case AgentFinishedEvent e -> System.out.println("  [EVENT] Beendet");
            }
        });

        var result = agent.execute("Hallo Welt");
        System.out.println("  Agent: " + result.finalAnswer());
        System.out.println("  StopReason: " + result.stopReason());
        System.out.println("  Tool-Calls: " + result.metrics().toolCallsCount()
            + ", " + result.metrics().durationMs() + " ms");
        System.out.println();
    }

    static void demoAgentTool() {
        System.out.println("─── 2. A2A: Agent ruft Sub-Agent als Tool auf ───");

        var subModel = new DemoMockModel();
        var subAgent = new StrandsAgent(subModel);
        subAgent.setEventListener(e -> {});

        var agentTool = new AgentTool(subAgent, "recherche",
            "Führt Recherchen in einer Wissensdatenbank durch");

        System.out.println("  AgentTool-Typ: " + agentTool.getClass().getSimpleName());
        System.out.println("  @Tool-Methode: execute(String prompt)");
        System.out.println("  Rekursionstiefe: max " + AgentTool.MAX_RECURSION_DEPTH);

        var registry = new ToolRegistry();
        registry.register(agentTool);
        System.out.println("  Registrierte Tools: " + registry.getToolNames());

        var parentModel = new DemoMockModel();
        var parentAgent = new StrandsAgent(parentModel, registry, new ToolExecutor());
        var result = parentAgent.execute("Recherchiere das Wetter");
        System.out.println("  Parent-Agent: " + result.finalAnswer());
        System.out.println();
    }

    static void demoSwarmOrchestrator() {
        System.out.println("─── 3. Swarm: Orchestrator routet nach Topic ───");

        var wetterAgent = new StrandsAgent(new DemoMockModel("Wetterbericht: %s"));
        var matheAgent = new StrandsAgent(new DemoMockModel("Mathe-Ergebnis: %s"));

        var defaultAgent = new StrandsAgent(new DemoMockModel("Allgemein: %s"));

        var orchestrator = new SwarmOrchestrator(Map.of(
            "wetter", wetterAgent,
            "mathe", matheAgent
        ), defaultAgent);

        System.out.println("  Routen: " + orchestrator.getRoutes().stream()
            .map(r -> r.topic()).toList());

        for (var prompt : List.of(
            "Wie wird das Wetter morgen?",
            "Löse diese Mathe-Aufgabe: 5 + 3",
            "Hallo, wie geht es dir?"
        )) {
            var result = orchestrator.execute(prompt);
            System.out.println("  Prompt: \"" + prompt + "\"");
            System.out.println("  → " + result.finalAnswer());
        }
        System.out.println();
    }

    static void demoAgentConfig() {
        System.out.println("─── 4. Config: AgentConfig-Builder ───");

        var config = AgentConfig.builder()
            .name("recherche-agent")
            .modelName("openai/gpt-4o")
            .toolClassNames(List.of("com.strands.agents.core.tools.CalculatorTool"))
            .maxIterations(15)
            .routes(Map.of("wetter", "weather-agent"))
            .build();

        System.out.println("  Name: " + config.name());
        System.out.println("  Model: " + config.modelName());
        System.out.println("  Tools: " + config.toolClassNames());
        System.out.println("  MaxIterations: " + config.maxIterations());
        System.out.println("  Routes: " + config.routes());
        System.out.println();
    }

    static class DemoMockModel implements ChatModel {
        private final String template;
        DemoMockModel() { this("Mock antwortet: %s"); }
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
