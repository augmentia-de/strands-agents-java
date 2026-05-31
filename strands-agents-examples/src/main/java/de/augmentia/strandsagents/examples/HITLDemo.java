package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.hook.HookRegistry;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLHook;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Scanner;

/**
 * HITLDemo zeigt den Human-in-the-Loop-Mechanismus via Hook-System.
 *
 * Der Agent darf erst ein Tool ausfuhren, nachdem der Mensch es per
 * Konsoleneingabe bestatigt hat (CONFIRM-Authority).
 */
public class HITLDemo {

    public static void main(String[] args) {
        System.out.println("=== HITL Demo (Hook-basiert) ===");
        System.out.println("Jeder Tool-Aufruf muss manuell bestatigt werden.\n");

        ChatModel model = ModelFactory.createOpenAiFromEnv();

        ToolRegistry toolRegistry = ToolRegistry.builder()
            .standard()
            .with("de.augmentia.strandsagents.core.tools.CalculatorTool")
            .build();

        ToolExecutor toolExecutor = new ToolExecutor();
        var conversationManager = new SlidingWindowConversationManager(10);

        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.register(new HITLHook(
            HITLHook.consoleProvider(),
            HITLAuthority.CONFIRM
        ));

        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            null,
            null,
            List.of(),
            hookRegistry
        );

        agent.setSystemPrompt("You are a helpful assistant with access to tools. "
            + "Always explain what you want to do before calling a tool.");

        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nDu: ");
                if (!scanner.hasNextLine()) break;
                var input = scanner.nextLine().strip();
                if (input.isBlank()) continue;

                if (input.equals("/exit") || input.equals("/quit")) {
                    System.out.println("Bye!");
                    break;
                }

                if (input.equals("/help")) {
                    System.out.println("Commands: /exit, /help");
                    System.out.println("Alles andere wird als Prompt gesendet.");
                    continue;
                }

                long start = System.nanoTime();
                AgentResult result;
                try {
                    result = agent.execute(input);
                } catch (Exception e) {
                    System.out.println("Fehler: " + e.getMessage());
                    continue;
                }
                var durationMs = (System.nanoTime() - start) / 1_000_000;

                System.out.println("\nAgent: " + result.finalAnswer());
                System.out.println("  " + durationMs + " ms, "
                    + result.metrics().toolCallsCount() + " Tool-Calls");
            }
        }
    }
}
