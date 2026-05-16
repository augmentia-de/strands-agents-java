package com.strands.agents.examples;

import com.strands.agents.core.*;
import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.event.*;

public class Main {

    public static void main(String[] args) {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Fehler: OPENAI_API_KEY ist nicht gesetzt.");
            System.out.println("  Nutze ./dev.sh run-mock für einen Demo-Durchlauf ohne API-Key.");
            System.out.println("  Oder setze die Umgebungsvariable: export OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        var agent = AgentConfig.builder()
            .name("demo")
            .toolRegistry(ToolRegistry.builder().standard().build())
            .build()
            .createAgent();

        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e ->
                    System.out.println("[EVENT] Gestartet – \"" + e.initialPrompt() + "\"");
                case ModelRequestedEvent e ->
                    System.out.println("[EVENT] LLM-Call (" + e.promptHistory().size() + " Nachrichten)");
                case ToolExecutionStartedEvent e ->
                    System.out.println("[EVENT] Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e ->
                    System.out.println("[EVENT] Tool-Result: " + e.result().toolName()
                        + " → " + e.result().result());
                case BeforeInvocationEvent e -> {}
                case AfterInvocationEvent e -> {}
                case AgentStateChangedEvent e -> {}
                case TokenEvent e -> {}
                case AgentFinishedEvent e ->
                    System.out.println("[EVENT] Beendet");
            }
        });

        var baseUrl = System.getenv("OPENAI_BASE_URL");
        var modelName = System.getenv("LLM_CHAT_MODEL");

        System.out.println("=== Strands Agent (OpenAI-kompatibel) ===");
        System.out.println("Session: " + agent.getSessionId());
        if (baseUrl != null && !baseUrl.isBlank()) {
            System.out.println("Base URL: " + baseUrl);
        }
        if (modelName != null && !modelName.isBlank()) {
            System.out.println("Model: " + modelName);
        }
        System.out.println("Tools: " + agent.getToolRegistry().getToolNames());
        System.out.println();

        interact(agent, "Hallo, wer bist du?");
        interact(agent, "Was kannst du?");
        interact(agent, "Erinnere dich: mein Name ist Torsten.");
        interact(agent, "Wie heiße ich?");
    }

    static void interact(StrandsAgent agent, String prompt) {
        System.out.println("---");
        System.out.println("Du:    " + prompt);
        AgentResult result = agent.execute(prompt);
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println("       StopReason: " + result.stopReason());
        System.out.println("       Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms"
            + ", Tool-Calls: " + result.metrics().toolCallsCount());
        System.out.println();
    }
}
