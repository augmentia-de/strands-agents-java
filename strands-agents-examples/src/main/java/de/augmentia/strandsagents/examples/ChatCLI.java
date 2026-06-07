package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.tools.McpToolMethod;
import de.augmentia.strandsagents.sessions.FileChatMemoryStore;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import de.augmentia.strandsagents.skills.AgentSkillsPlugin;
import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.skills.SkillParser;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ChatCLI {

    private static McpClient mcpClient;

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
                System.err.println("OPENAI_API_KEY not set. Use --mock for demo mode.");
                System.exit(1);
            }
            model = ModelFactory.createOpenAiFromEnv();
            var baseUrl = System.getenv("OPENAI_BASE_URL");
            var modelName = System.getenv("OPENAI_MODEL");
            System.out.println("=== Strands Chat (OpenAI) ===");
            if (baseUrl != null) System.out.println("  Base URL: " + baseUrl);
            if (modelName != null) System.out.println("  Model: " + modelName);
        }

        System.out.println("  LLM-Log: logs/llm-calls.log (10×2MB rotating)");

        var registry = ToolRegistry.builder()
                .workspace(Path.of(".").toAbsolutePath())
            .standard()
            .with("de.augmentia.strandsagents.core.tools.CalculatorTool")
            .build();

        var mcpCommand = System.getenv("MCP_SERVER_COMMAND");
        var mcpUrl = System.getenv("MCP_SERVER_URL");
        if (mcpCommand != null && !mcpCommand.isBlank()) {
            connectMcpStdio(registry, mcpCommand);
        } else if (mcpUrl != null && !mcpUrl.isBlank()) {
            connectMcpSse(registry, mcpUrl);
        }

        var conversationManager = new SlidingWindowConversationManager(20);
        var sessionManager = new FileSessionManager(sessionDir);
        var chatMemoryStore = new FileChatMemoryStore(Path.of(".chat-memory"));

        var skillsDir = Path.of("skills");
        List<Plugin> plugins = List.of();
        if (Files.isDirectory(skillsDir)) {
            try {
                var skills = SkillParser.fromDirectory(skillsDir);
                if (!skills.isEmpty()) {
                    plugins = List.of(new AgentSkillsPlugin(skills));
                    System.out.println("  Skills: " + skills.stream().map(Skill::name).toList());
                }
            } catch (Exception e) {
                System.out.println("  Skills error: " + e.getMessage());
            }
        }

        var agent = AgentConfig.builder()
            .toolRegistry(registry)
            .conversationManager(conversationManager)
            .sessionManager(sessionManager)
            .chatMemoryStore(chatMemoryStore)
            .plugins(plugins)
            .logLlmCalls(Path.of("logs/llm-calls.log"))
            .build()
            .createAgent(model);

        var actualSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        var session = sessionManager.createSession("chat-user", Map.of());

        agent.setEventListener(event -> {
            switch (event) {
                case AgentStartedEvent e -> System.out.println("\n  Agent started");
                case ModelRequestedEvent e -> System.out.println("  LLM-Call (" + e.promptHistory().size() + " messages)");
                case ToolExecutionStartedEvent e -> System.out.println("  Tool: " + e.toolCall().toolName());
                case ToolExecutionFinishedEvent e ->
                    System.out.println("  " + e.result().toolName() + " -> " + truncate(e.result().result(), 80));
                case BeforeInvocationEvent e -> {}
                case AfterInvocationEvent e -> {}
                case AgentStateChangedEvent e -> System.out.println("  " + e.previousPhase() + " → " + e.currentPhase());
                case TokenEvent e -> {}
                case AgentFinishedEvent e -> {}
            }
        });

        System.out.println("  Session: " + actualSessionId);
        System.out.println("  Tools: " + registry.getToolNames());
        System.out.println("  Session-Dir: " + sessionDir.toAbsolutePath());
        System.out.println();
        System.out.println("Commands: /exit, /tools, /session, /help");
        System.out.println("-".repeat(50));

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
                        System.out.println("  Error: " + e.getMessage());
                    continue;
                }
                var durationMs = (System.nanoTime() - start) / 1_000_000;

                System.out.println("  Agent: " + result.finalAnswer());
                System.out.println("  - " + result.metrics().inputTokens() + " in / "
                    + result.metrics().outputTokens() + " out, "
                    + durationMs + " ms, "
                    + result.metrics().toolCallsCount() + " Tool-Calls");
            }
        }
        closeMcp();
    }

    static boolean handleCommand(String input, Agent agent, ToolRegistry registry, String sessionId) {
        var parts = input.split("\\s+", 2);
        switch (parts[0].toLowerCase()) {
            case "/exit", "/quit" -> {
                System.out.println("  Bye!");
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
                System.out.println("  Messages: " + history.size());
                for (var msg : history) {
                    var role = msg.type().name();
                    var text = msg instanceof dev.langchain4j.data.message.AiMessage ai
                        ? ai.text() : msg instanceof dev.langchain4j.data.message.UserMessage um
                        ? um.singleText() : msg.toString();
                    System.out.println("    [" + role + "] " + truncate(text, 100));
                }
            }
            case "/mcp" -> {
                if (mcpClient == null) {
                    System.out.println("  No MCP server connected.");
                    System.out.println("  Set MCP_SERVER_COMMAND or MCP_SERVER_URL.");
                } else {
                    System.out.println("  MCP server: connected");
                    try {
                        var tools = mcpClient.listTools();
                        System.out.println("  Tools (" + tools.size() + "):");
                        for (var spec : tools) {
                            System.out.println("    - " + spec.name() + ": " + (spec.description() != null ? spec.description() : ""));
                        }
                    } catch (Exception e) {
                    System.out.println("  Error: " + e.getMessage());
                    }
                }
            }
            case "/help" -> {
                System.out.println("  Commands:");
                System.out.println("    /exit            Exit");
                System.out.println("    /tools           Show registered tools");
                System.out.println("    /session         Show current session & history");
                System.out.println("    /mcp             MCP server status & tools");
                System.out.println("    /help            This help");
                System.out.println("  Everything else is sent as a prompt to the agent.");
            }
            default -> System.out.println("  Unknown command: " + parts[0] + " (/help for help)");
        }
        return true;
    }

    static void connectMcpStdio(ToolRegistry registry, String command) {
        try {
            var parts = command.split("\\s+");
            var transport = StdioMcpTransport.builder()
                .command(List.of(parts)).build();
            mcpClient = DefaultMcpClient.builder().transport(transport).build();
            var tools = mcpClient.listTools();
            System.out.println("  MCP (Stdio): " + String.join(" ", parts) + " -> " + tools.size() + " Tools");
            for (var spec : tools) {
                registry.register(spec.name(), spec, new McpToolMethod(mcpClient, String.join(" ", parts), spec.name(), spec));
                System.out.println("    - " + spec.name() + ": " + spec.description());
            }
        } catch (Exception e) {
            System.out.println("  MCP error: " + e.getMessage());
            if (mcpClient != null) try { mcpClient.close(); } catch (Exception ignored) {}
            mcpClient = null;
        }
    }

    static void connectMcpSse(ToolRegistry registry, String url) {
        try {
            var transport = StreamableHttpMcpTransport.builder().url(url).build();
            mcpClient = DefaultMcpClient.builder().transport(transport).build();
            var tools = mcpClient.listTools();
            System.out.println("  MCP (SSE): " + url + " -> " + tools.size() + " Tools");
            for (var spec : tools) {
                registry.register(spec.name(), spec, new McpToolMethod(mcpClient, url, spec.name(), spec));
                System.out.println("    - " + spec.name() + ": " + spec.description());
            }
        } catch (Exception e) {
            System.out.println("  MCP error: " + e.getMessage());
            if (mcpClient != null) try { mcpClient.close(); } catch (Exception ignored) {}
            mcpClient = null;
        }
    }

    static void closeMcp() {
        if (mcpClient != null) {
            try { mcpClient.close(); } catch (Exception ignored) {}
            mcpClient = null;
        }
    }

    static void printHelp() {
        System.out.println("""
            Usage: ChatCLI [Options]
            Options:
              --mock              Start without API key in mock mode
              --session <id>      Resume existing session
              --session-dir <path> Directory for session persistence
              --help              This help

            Environment variables (optional):
              MCP_SERVER_COMMAND  MCP server via Stdio (e.g. "npx -y ...")
              MCP_SERVER_URL      MCP server via HTTP/SSE (e.g. "http://...")
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
                        "Mock-Agent:\n\nYou said: \"" + text + "\"\n\n"
                        + "This is a simulated response. In real mode (without --mock) "
                        + "the OpenAI API would respond."))
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
