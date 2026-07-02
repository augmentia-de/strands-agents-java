package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.interceptor.pipeline.AgentHook;
import de.augmentia.strandsagents.interceptor.pipeline.HookContexts;
import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.interceptor.pipeline.HookResult;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailResult;
import de.augmentia.strandsagents.interceptor.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import de.augmentia.strandsagents.tools.builtin.BashTool;
import de.augmentia.strandsagents.tools.HumanInTheLoopTool;
import de.augmentia.strandsagents.tools.builtin.ReadTool;
import de.augmentia.strandsagents.core.sessions.FileSessionManager;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;
import java.util.List;

/**
 * AgentDemo showcases the comprehensive instantiation of a Agent.
 * 
 * It demonstrates how to manually configure all components:
 * 1. LLM ChatModel
 * 2. ToolRegistry (with custom and standard tools)
 * 3. ToolExecutor (responsible for tool invocation)
 * 4. ConversationManager (manages chat history/context)
 * 5. SessionManager (persists agent state and messages)
 * 6. ResilienceConfig (Retry and Circuit Breaker logic)
 * 7. Plugins (Guardrails and HITL)
 * 8. Hooks (logging via HookRegistry + AgentHook)
 */
public class AgentDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Initializing Comprehensive Agent Demo...");

        // 1. ChatModel: The core LLM (using OpenAI from environment variables)
        ChatModel model = ModelFactory.createOpenAiFromEnv();
        CheckpointService cpService = new CheckpointService(
                System.getenv("STRANDS_AGENT_HITL_TOOLS"), 120_000);
        cpService.registerChannel(new ConsoleChannel());
        // 2. ToolRegistry: Registering tools the agent can use
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new BashTool(Path.of(""))); // Allows executing bash commands
        toolRegistry.register(new ReadTool(Path.of(""))); // Allows reading files
        toolRegistry.register(new HumanInTheLoopTool(cpService)); // Allows asking the human for help

        // 3. ToolExecutor: The engine that runs the tools
        ToolExecutor toolExecutor = new DefaultToolExecutor();

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

        List<Plugin> plugins = List.of(guardrails);

        // 8. Hooks: interception points with flow control (cancel, modify, retry)
        AgentHook loggingHook = new AgentHook() {
            @Override
            public String name() { return "logging"; }

            @Override
            public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
                System.out.println("🪝 Hook: agent execution starting");
                return new HookResult.Continue();
            }

            @Override
            public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
                System.out.println("🪝 Hook: agent execution finished");
                return new HookResult.Modify<>(response);
            }

            @Override
            public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
                System.out.println("🪝 Hook: about to call LLM (" + ctx.messages().size() + " messages)");
                return new HookResult.Continue();
            }

            @Override
            public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
                System.out.println("🪝 Hook: LLM responded (" + response.length() + " chars)");
                return new HookResult.Modify<>(response);
            }

            @Override
            public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
                System.out.println("🪝 Hook: about to call tool '" + ctx.toolName() + "'");
                return new HookResult.Continue();
            }

            @Override
            public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
                System.out.println("🪝 Hook: tool '" + ctx.toolName() + "' returned (" + result.length() + " chars)");
                return new HookResult.Modify<>(result);
            }
        };

        HookRegistry hookRegistry = new HookRegistry();
        hookRegistry.register(loggingHook);

        // --- INSTANTIATION ---
        // Using the 8-param constructor with both plugins and hooks
        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            sessionManager,
            resilienceConfig,
            plugins,
            hookRegistry
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
