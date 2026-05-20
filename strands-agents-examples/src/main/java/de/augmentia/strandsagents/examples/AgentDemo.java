package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLPlugin;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.BashTool;
import de.augmentia.strandsagents.core.tools.HumanInTheLoopTool;
import de.augmentia.strandsagents.core.tools.ReadTool;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;
import java.util.List;

/**
 * AgentDemo showcases the comprehensive instantiation of a StrandsAgent.
 * 
 * It demonstrates how to manually configure all components:
 * 1. LLM ChatModel
 * 2. ToolRegistry (with custom and standard tools)
 * 3. ToolExecutor (responsible for tool invocation)
 * 4. ConversationManager (manages chat history/context)
 * 5. SessionManager (persists agent state and messages)
 * 6. ResilienceConfig (Retry and Circuit Breaker logic)
 * 7. Plugins (Guardrails and HITL)
 */
public class AgentDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Initializing Comprehensive StrandsAgent Demo...");

        // 1. ChatModel: The core LLM (using OpenAI from environment variables)
        ChatModel model = ModelFactory.createOpenAiFromEnv();

        // 2. ToolRegistry: Registering tools the agent can use
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new BashTool(Path.of(""))); // Allows executing bash commands
        toolRegistry.register(new ReadTool(Path.of(""))); // Allows reading files
        toolRegistry.register(new HumanInTheLoopTool()); // Allows asking the human for help

        // 3. ToolExecutor: The engine that runs the tools
        ToolExecutor toolExecutor = new ToolExecutor();

        // 4. ConversationManager: Handles chat history (e.g., sliding window of 10 messages)
        ConversationManager conversationManager = new SlidingWindowConversationManager(10);

        // 5. SessionManager: Persists sessions to local JSON files
        SessionManager sessionManager = new FileSessionManager(Path.of("logs/sessions"));

        // 6. ResilienceConfig: Defines how to handle failures (Retries and Circuit Breakers)
        ResilienceConfig resilienceConfig = new ResilienceConfig(
            new RetryConfig(3, 1000, 2.0), // 3 retries, starting at 1s, doubling each time
            new CircuitBreakerConfig(0.5f, 10L, 30L) // 50% failure rate, 10s window, 30s half-open
        );

        // 7. Plugins: Extending agent behavior (Guardrails and HITL)
        GuardrailPlugin guardrails = new GuardrailPlugin(
            List.of((messages, context) -> {
                System.out.println("🛡️ Guardrail checking input...");
                return GuardrailResult.ok();
            }),
            List.of((messages, context) -> {
                System.out.println("🛡️ Guardrail checking output...");
                return GuardrailResult.ok();
            })
        );

        HITLPlugin hitl = new HITLPlugin(
            new HumanInTheLoopTool.ConsoleHITLProvider(),
            HITLAuthority.CONFIRM // Requires human confirmation for all tool calls
        );

        List<Plugin> plugins = List.of(guardrails, hitl);

        // --- INSTANTIATION ---
        // Using the most comprehensive constructor available
        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            sessionManager,
            resilienceConfig,
            plugins
        );

        // Set a system prompt to guide the agent's persona
        agent.setSystemPrompt("You are a highly capable and secure assistant. " +
                "Always verify actions with the user when using tools.");

        // --- EXECUTION ---
        String prompt = "What is the content of the file 'README.md'?";
        System.out.println("\n[User]: " + prompt);

        AgentResult result = agent.execute(prompt);

        // --- OUTPUT ---
        System.out.println("\n[Agent]: " + result.finalAnswer());
        System.out.println("\n--- METRICS ---");
        System.out.println("Duration: " + result.metrics().durationMs() + "ms");
        System.out.println("Tool Calls: " + result.metrics().toolCallsCount());
        System.out.println("Stop Reason: " + result.stopReason());
    }
}
