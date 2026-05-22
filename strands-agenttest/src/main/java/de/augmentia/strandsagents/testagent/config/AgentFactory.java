package de.augmentia.strandsagents.testagent.config;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.conversation.SummarizingConversationManager;
import de.augmentia.strandsagents.core.hook.AgentHook;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookRegistry;
import de.augmentia.strandsagents.core.hook.HookResult;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.guardrail.BlockAction;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLHook;
import de.augmentia.strandsagents.core.plugin.hitl.HITLPlugin;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.CalculatorTool;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private AgentFactory() {}

    public static Agent fromConfig(TestConfig config) {
        var model = createModel(config.model());

        var toolRegistry = createToolRegistry(config.tools());

        var convMgr = createConversationManager(config.conversation());

        var sessionMgr = createSessionManager(config.session());

        var resilience = createResilience(config.resilience());

        var plugins = createPlugins(config.plugins());

        var hookRegistry = createHookRegistry(config.hooks());

        var agent = new Agent(model, toolRegistry, new ToolExecutor(),
            convMgr, sessionMgr, resilience, plugins, hookRegistry);

        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            agent.setSystemPrompt(config.systemPrompt());
        }

        if (config.structuredOutput() != null && config.structuredOutput().enabled()) {
            var so = config.structuredOutput();
            if (so.outputClass() != null && !so.outputClass().isBlank()) {
                try {
                    agent.setStructuredOutputModel(Class.forName(so.outputClass()));
                } catch (ClassNotFoundException e) {
                    log.error("StructuredOutput class not found: {}", so.outputClass());
                }
            }
        }

        return agent;
    }

    static dev.langchain4j.model.chat.ChatModel createModel(
            TestConfig.ModelConfig cfg) {
        return switch (cfg.type()) {
            case "mock" -> new MockChatModel(
                cfg.responseTemplate() != null ? cfg.responseTemplate()
                    : "Mock-Antwort: %s");
            case "openai" -> ModelFactory.createOpenAiFromEnv();
            case "openai-from-env" -> ModelFactory.createOpenAiFromEnv();
            default -> new MockChatModel();
        };
    }

    static ToolRegistry createToolRegistry(TestConfig.ToolConfig cfg) {
        if (cfg == null) return new ToolRegistry();

        var builder = switch (cfg.preset()) {
            case "standard" -> ToolRegistry.builder().standard();
            case "empty" -> ToolRegistry.builder();
            case "minimal" -> ToolRegistry.builder()
                .with(new CalculatorTool());
            default -> ToolRegistry.builder();
        };

        if (cfg.additional() != null) {
            for (var cn : cfg.additional()) {
                builder.with(cn);
            }
        }
        if (cfg.include() != null && !cfg.include().isEmpty()) {
            builder.include(cfg.include().toArray(new String[0]));
        }
        if (cfg.exclude() != null && !cfg.exclude().isEmpty()) {
            builder.exclude(cfg.exclude().toArray(new String[0]));
        }
        return builder.build();
    }

    static ConversationManager createConversationManager(
            TestConfig.ConversationConfig cfg) {
        if (cfg == null || cfg.type() == null) return null;
        return switch (cfg.type()) {
            case "sliding" -> new SlidingWindowConversationManager(
                cfg.windowSize() > 0 ? cfg.windowSize() : 10);
            case "summarizing" -> new SummarizingConversationManager(
                ModelFactory.createOpenAiFromEnv(), 2048);
            default -> null;
        };
    }

    static de.augmentia.strandsagents.sessions.SessionManager createSessionManager(
            TestConfig.SessionConfig cfg) {
        if (cfg == null || cfg.type() == null) return null;
        return switch (cfg.type()) {
            case "file" -> new FileSessionManager(
                Path.of(cfg.directory() != null ? cfg.directory() : ".sessions"));
            default -> null;
        };
    }

    static ResilienceConfig createResilience(
            TestConfig.ResilienceBlock cfg) {
        if (cfg == null || !cfg.enabled()) return null;
        var r = cfg.retry();
        var cb = cfg.circuitBreaker();
        var retry = r != null
            ? new RetryConfig(r.maxAttempts(), r.backoffDelayMs(),
                r.backoffMultiplier())
            : null;
        var breaker = cb != null
            ? new CircuitBreakerConfig(cb.failureRateThreshold(),
                cb.slidingWindowSeconds(), cb.halfOpenDelaySeconds())
            : null;
        if (retry == null && breaker == null) return null;
        return new ResilienceConfig(retry, breaker);
    }

    static List<Plugin> createPlugins(TestConfig.PluginBlock cfg) {
        var plugins = new ArrayList<Plugin>();
        if (cfg == null) return plugins;

        if (cfg.guardrail() != null && cfg.guardrail().enabled()) {
            plugins.add(new GuardrailPlugin(
                List.of((msgs, ctx) -> de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult.ok()),
                List.of((msgs, ctx) -> de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult.ok()),
                BlockAction.valueOf(cfg.guardrail().blockAction()),
                cfg.guardrail().fallbackMessage()
            ));
        }

        if (cfg.hitl() != null && cfg.hitl().enabled()) {
            plugins.add(new HITLPlugin(
                createAlwaysApproveProvider(),
                HITLAuthority.valueOf(cfg.hitl().authority())
            ));
        }

        return plugins;
    }

    static de.augmentia.strandsagents.core.plugin.hitl.HITLProvider createAlwaysApproveProvider() {
        return (action, context) -> new de.augmentia.strandsagents.core.plugin.guardrail.ApprovalResult(
            action, true, "auto-approved", java.time.Instant.now());
    }

    static HookRegistry createHookRegistry(List<TestConfig.HookEntry> hooks) {
        var registry = new HookRegistry();
        if (hooks == null) return registry;

        for (var entry : hooks) {
            if (!entry.enabled()) continue;
            switch (entry.name()) {
                case "logging" -> registry.register(new LoggingAgentHook());
                default -> log.warn("Unknown hook: {}", entry.name());
            }
        }
        return registry;
    }

    private record LoggingAgentHook() implements AgentHook {
        @Override
        public String name() { return "logging"; }

        @Override
        public HookResult beforeAgent(HookContexts.BeforeAgentContext ctx) {
            log.debug("[HOOK] beforeAgent: {}", ctx.prompt());
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response) {
            log.debug("[HOOK] afterAgent: duration={}ms", ctx.result().metrics().durationMs());
            return new HookResult.Continue();
        }

        @Override
        public HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx) {
            log.debug("[HOOK] beforeModelCall: {} messages", ctx.messages().size());
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String response) {
            log.debug("[HOOK] afterModelCall: {} chars", response.length());
            return new HookResult.Continue();
        }

        @Override
        public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
            log.debug("[HOOK] beforeToolCall: {}", ctx.toolName());
            return new HookResult.Continue();
        }

        @Override
        public HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String result) {
            log.debug("[HOOK] afterToolCall: {} → {} chars", ctx.toolName(), result.length());
            return new HookResult.Continue();
        }
    }
}
