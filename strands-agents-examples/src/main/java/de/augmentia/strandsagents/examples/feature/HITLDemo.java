package de.augmentia.strandsagents.examples.feature;

import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.hitl.HITLAuthority;
import de.augmentia.strandsagents.interceptor.hitl.HITLPlugin;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Scanner;

public class HITLDemo {

    public static void main(String[] args) {
        System.out.println("=== HITL Demo (Plugin-based) ===");
        System.out.println("Each tool call must be manually confirmed.\n");

        ChatModel model = ModelFactory.createOpenAiFromEnv();

        ToolRegistry toolRegistry = ToolRegistry.builder()
            .standard()
            .with("de.augmentia.strandsagents.tools.builtin.CalculatorTool")
            .build();

        ToolExecutor toolExecutor = new DefaultToolExecutor();
        var conversationManager = new SlidingWindowConversationManager(10);

        var hitlPlugin = new HITLPlugin(
            HITLPlugin.consoleProvider(),
            HITLAuthority.CONFIRM
        );
        List<Plugin> plugins = List.of(hitlPlugin);

        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            null,
            null,
            plugins
        );

        agent.setSystemPrompt("You are a helpful assistant with access to tools. "
            + "Always explain what you want to do before calling a tool.");

        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nYou: ");
                if (!scanner.hasNextLine()) break;
                var input = scanner.nextLine().strip();
                if (input.isBlank()) continue;

                if (input.equals("/exit") || input.equals("/quit")) {
                    System.out.println("Bye!");
                    break;
                }

                if (input.equals("/help")) {
                    System.out.println("Commands: /exit, /help");
                    System.out.println("Everything else is sent as a prompt.");
                    continue;
                }

                long start = System.nanoTime();
                AgentResult result;
                try {
                    result = agent.execute(input);
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                    continue;
                }
                long durationMs = (System.nanoTime() - start) / 1_000_000;

                System.out.println("\nAgent [" + result.stopReason() + "] (" + durationMs + "ms):");
                System.out.println(result.finalAnswer());
            }
        }
    }
}
