package de.augmentia.strandsagents.examples.feature;

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
 * HITLDemo demonstrates the Human-in-the-Loop mechanism via Hook system.
 *
 * The agent may only execute a tool after the human has confirmed it
 * via console input (CONFIRM authority).
 */
public class HITLDemo {

    public static void main(String[] args) {
        System.out.println("=== HITL Demo (Hook-based) ===");
        System.out.println("Each tool call must be manually confirmed.\n");

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
                    System.out.println("Error: " + e.getMessage());
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
