package com.strands.agents.examples;

import com.strands.agents.core.*;
import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.event.*;
import com.strands.agents.core.model.message.*;
import com.strands.agents.core.tools.CalculatorTool;
import com.strands.agents.sessions.FileSessionManager;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Path;
import java.util.*;

public class ChatCLI {

    public static void main(String[] args) {
        var useMock = false;
        var sessionId = (String) null;
        var sessionDir = Path.of(".sessions");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mock" -> useMock = true;
                case "--session" -> { if (i + 1 < args.length) sessionId = args[++i]; }
                case "--session-dir" -> { if (i + 1 < args.length) sessionDir = Path.of(args[++i]); }
                case "--help" -> { printHelp(); return; }
            }
        }

        ChatModel model;
        if (useMock) {
            model = createMockModel();
            System.out.println("=== Strands Chat (Mock) ===");
        } else {
            var apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                System.err.println("OPENAI_API_KEY nicht gesetzt. Nutze --mock für Demo-Modus.");
                System.exit(1);
            }
            model = ModelFactory.createOpenAiFromEnv();
            var baseUrl = System.getenv("OPENAI_BASE_URL");
            var modelName = System.getenv("LLM_CHAT_MODEL");
            System.out.println("=== Strands Chat (OpenAI) ===");
            if (baseUrl != null) System.out.println("  Base URL: " + baseUrl);
            if (modelName != null) System.out.println("  Model: " + modelName);
        }

        var registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        var conversationManager = new SlidingWindowConversationManager(20);
        var sessionManager = new FileSessionManager(sessionDir);
        var agent = new StrandsAgent(model, registry, new ToolExecutor(), conversationManager, sessionManager);

        var actualSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        var session = sessionManager.createSession("chat-user", Map.of());

        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e -> System.out.println("\n  ⏳ Agent gestartet");
                case ModelRequestedEvent e -> System.out.println("  🧠 LLM-Call (" + e.promptHistory().size() + " Nachrichten)");
                case ToolExecutionStartedEvent e -> System.out.println("  🔧 Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e ->
                    System.out.println("  ✅ " + e.result().toolName() + " → " + truncate(e.result().result(), 80));
                case AgentFinishedEvent e -> {}
            }
        });

        System.out.println("  Session: " + actualSessionId);
        System.out.println("  Tools: " + registry.getToolNames());
        System.out.println("  Session-Dir: " + sessionDir.toAbsolutePath());
        System.out.println();
        System.out.println("Commands: /exit, /tools, /session, /help");
        System.out.println("─".repeat(50));

        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nDu: ");
                if (!scanner.hasNextLine()) break;
                var input = scanner.nextLine().strip();
                if (input.isBlank()) continue;

                if (input.startsWith("/")) {
                    if (!handleCommand(input, agent, registry, actualSessionId)) break;
                    continue;
                }

                long start = System.nanoTime();
                AgentResult result;
                try {
                    result = agent.execute(actualSessionId, input, Map.of());
                } catch (Exception e) {
                    System.out.println("  ⚠️ Fehler: " + e.getMessage());
                    continue;
                }
                var durationMs = (System.nanoTime() - start) / 1_000_000;

                System.out.println("  Agent: " + result.finalAnswer());
                System.out.println("  ─ " + result.metrics().inputTokens() + " in / "
                    + result.metrics().outputTokens() + " out, "
                    + durationMs + " ms, "
                    + result.metrics().toolCallsCount() + " Tool-Calls");
            }
        }
    }

    static boolean handleCommand(String input, StrandsAgent agent, ToolRegistry registry, String sessionId) {
        var parts = input.split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "/exit", "/quit" -> {
                System.out.println("  👋 Tschüss!");
                return false;
            }
            case "/tools" -> {
                System.out.println("  Registrierte Tools: " + registry.getToolNames());
                for (var name : registry.getToolNames()) {
                    var tm = registry.get(name);
                    System.out.println("    - " + name + ": " + tm.spec().description());
                }
            }
            case "/session" -> {
                System.out.println("  Session-ID: " + sessionId);
                var history = agent.getChatMemory().messages();
                System.out.println("  Nachrichten: " + history.size());
                for (var msg : history) {
                    var role = msg.type().name();
                    var text = msg instanceof dev.langchain4j.data.message.AiMessage ai
                        ? ai.text() : msg instanceof dev.langchain4j.data.message.UserMessage um
                        ? um.singleText() : msg.toString();
                    System.out.println("    [" + role + "] " + truncate(text, 100));
                }
            }
            case "/help" -> {
                System.out.println("  Commands:");
                System.out.println("    /exit            Beenden");
                System.out.println("    /tools           Registrierte Tools anzeigen");
                System.out.println("    /session         Aktuelle Session & Verlauf");
                System.out.println("    /help            Diese Hilfe");
                System.out.println("  Alles andere wird als Prompt an den Agenten gesendet.");
            }
            default -> System.out.println("  Unbekanntes Command: " + parts[0] + " (/help für Hilfe)");
        }
        return true;
    }

    static void printHelp() {
        System.out.println("""
            Nutzung: ChatCLI [Optionen]
            Optionen:
              --mock              Ohne API-Key im Mock-Modus starten
              --session <id>      Bestehende Session fortsetzen
              --session-dir <pfad> Verzeichnis für Session-Persistierung
              --help              Diese Hilfe
            """);
    }

    static ChatModel createMockModel() {
        return new ChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                var messages = request.messages();
                if (messages.isEmpty()) {
                    return dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from(""))
                        .tokenUsage(new dev.langchain4j.model.output.TokenUsage(0, 0))
                        .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                        .build();
                }
                var last = messages.get(messages.size() - 1);
                var text = last instanceof dev.langchain4j.data.message.UserMessage um
                    ? um.singleText() : last.toString();
                return dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from(
                        "🧠 Mock-Agent:\n\nDu sagtest: \"" + text + "\"\n\n"
                        + "Das ist eine simulierte Antwort. Im echten Modus (ohne --mock) "
                        + "würde hier die OpenAI-API antworten."))
                    .tokenUsage(new dev.langchain4j.model.output.TokenUsage(10, text.length()))
                    .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                    .build();
            }
        };
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
