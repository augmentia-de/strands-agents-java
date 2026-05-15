package com.strands.agents.examples;

import com.strands.agents.core.ModelFactory;
import com.strands.agents.core.StrandsAgent;
import com.strands.agents.core.model.agent.AgentResult;

public class Main {

    public static void main(String[] args) {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Fehler: OPENAI_API_KEY ist nicht gesetzt.");
            System.out.println("  Nutze ./dev.sh run-mock für einen Demo-Durchlauf ohne API-Key.");
            System.out.println("  Oder setze die Umgebungsvariable: export OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        var model = ModelFactory.createOpenAiFromEnv();
        var agent = new StrandsAgent(model);
        var sessionId = agent.getSessionId();

        var baseUrl = System.getenv("OPENAI_BASE_URL");
        var modelName = System.getenv("LLM_CHAT_MODEL");

        System.out.println("=== Strands Agent (OpenAI-kompatibel) ===");
        System.out.println("Session: " + sessionId);
        if (baseUrl != null && !baseUrl.isBlank()) {
            System.out.println("Base URL: " + baseUrl);
        }
        if (modelName != null && !modelName.isBlank()) {
            System.out.println("Model: " + modelName);
        }
        System.out.println();

        interact(agent, "Hallo, wer bist du?");
        interact(agent, "Was kannst du?");
        interact(agent, "Erinnere dich: mein Name ist Torsten.");
        interact(agent, "Wie heißt ich?");
    }

    static void interact(StrandsAgent agent, String prompt) {
        System.out.println("Du:    " + prompt);
        AgentResult result = agent.execute(prompt);
        System.out.println("Agent: " + result.finalAnswer());
        System.out.println("       (Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms)");
        System.out.println();
    }
}
