package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.AgentSettings;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.config.StrandsAgentConfig;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.EmailChannel;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.interceptor.hitl.HITLPlugin;
import de.augmentia.strandsagents.skills.Skill;
import de.augmentia.strandsagents.skills.AgentSkillsPlugin;
import de.augmentia.strandsagents.core.sessions.FileSessionManager;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.core.routing.LlmRouter;
import de.augmentia.strandsagents.config.TieredModelConfig;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.interceptor.telemetry.FileLlmLogger;
import de.augmentia.strandsagents.interceptor.telemetry.LoggingChatModel;
import de.augmentia.strandsagents.tools.ListToolsTool;
import de.augmentia.strandsagents.tools.builtin.HttpTool;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for assembling fully configured {@link Agent} instances from configuration.
 * <p>
 * Provides static methods to create tools, sessions, conversations, plugins, and agents
 * from {@link StrandsAgentConfig} and {@link AgentSettings} objects.
 * </p>
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private AgentFactory() {}

    /**
     * Creates a {@link ToolRegistry} with standard tools, HTTP tool, and extra tools from config.
     */
    public static ToolRegistry createToolRegistry(StrandsAgentConfig config) {
        var workspace = config.resolvedWorkspace();
        var builder = ToolRegistry.builder()
            .standard(config.bashAllowed())
            .workspace(workspace);
        builder.with(new HttpTool(config.httpAllowPrivate()));
        if (!config.extraTools().isBlank()) {
            for (var cn : config.extraTools().split(",")) {
                cn = cn.strip();
                if (!cn.isEmpty()) builder.with(cn);
            }
        }
        var registry = builder.build();
        registry.register(new ListToolsTool(registry));
        return registry;
    }

    /**
     * Creates a {@link CheckpointService} with console, SSE (optional), and email channels.
     */
    public static CheckpointService createCheckpointService(StrandsAgentConfig config, SSEChannel sseChannel) {
        var svc = new CheckpointService(config.hitlTools(), 120_000);
        svc.registerChannel(new ConsoleChannel());
        if (sseChannel != null) {
            svc.registerChannel(sseChannel);
        }
        if (config.hitlEmailRecipient() != null && !config.hitlEmailRecipient().isBlank()) {
            svc.registerChannel(new EmailChannel());
        }
        return svc;
    }

    /**
     * Creates a {@link CheckpointService} without an SSE channel.
     */
    public static CheckpointService createCheckpointService(StrandsAgentConfig config) {
        return createCheckpointService(config, null);
    }

    /**
     * Creates a file-based session manager in the given directory.
     */
    public static SessionManager createSessionManager(Path sessionDir) {
        try {
            Files.createDirectories(sessionDir);
        } catch (Exception ignored) {}
        return new FileSessionManager(sessionDir);
    }

    /**
     * Creates a sliding-window conversation manager with the given window size.
     */
    public static ConversationManager createConversationManager(int windowSize) {
        return new SlidingWindowConversationManager(windowSize);
    }

    /**
     * Builds the plugin chain from skills and guardrails, sorted by execution order.
     */
    public static List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills,
                                           boolean skillSearchEnabled) {
        var plugins = new ArrayList<Plugin>();
        if (skills != null && !skills.isEmpty()) {
            var skillsPlugin = new AgentSkillsPlugin(skills, initialSkills != null ? initialSkills : List.of());
            skillsPlugin.setSkillSearchEnabled(skillSearchEnabled);
            plugins.add(skillsPlugin);
        }
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        sortPlugins(plugins);
        return List.copyOf(plugins);
    }

    /**
     * Builds a default plugin list (empty skills, guardrails only).
     */
    public static List<Plugin> buildPlugins() {
        return buildPlugins(List.of(), List.of(), false);
    }

    /**
     * Sorts plugins by their execution order.
     */
    public static List<Plugin> sortPlugins(List<Plugin> plugins) {
        plugins.sort(Comparator.comparingInt(Plugin::order));
        return plugins;
    }

    /**
     * Creates a fully wired {@link Agent} with optional checkpoint service.
     */
    public static Agent createAgent(ChatModel model, ToolRegistry tools,
                                    SessionManager sessionManager,
                                    CheckpointService cpService,
                                    List<Plugin> plugins) {
        var agent = new Agent(model, tools, new DefaultToolExecutor(),
            null, sessionManager, null, plugins);
        if (cpService != null) {
            agent.setCheckpointService(cpService);
            wireCheckpointService(agent, cpService);
        }
        return agent;
    }

    /**
     * Builds an agent from settings and infrastructure config using an env-created model.
     */
    public static Agent buildAgent(AgentSettings settings, AgentConfig infra) {
        return buildAgent(settings, infra, ModelFactory.createOpenAiFromEnv());
    }

    /**
     * Builds an agent from settings and infrastructure config with a specific model.
     * <p>
     * Applies system prompt, structured output config, LLM logger, and tool iteration limit.
     * </p>
     */
    public static Agent buildAgent(AgentSettings settings, AgentConfig infra, ChatModel model) {
        var effectiveRegistry = infra.toolRegistry() != null ? infra.toolRegistry() : new ToolRegistry();
        var effectivePlugins = infra.plugins() != null ? infra.plugins() : List.<Plugin>of();

        ChatModel effectiveModel = model;
        FileLlmLogger logger = null;
        if (settings.llmLogPath() != null) {
            logger = new FileLlmLogger(settings.llmLogPath());
            effectiveModel = new LoggingChatModel(model, logger);
        }

        var agent = new Agent(effectiveModel, effectiveRegistry, new DefaultToolExecutor(),
            infra.conversationManager(), infra.sessionManager(), infra.chatMemoryStore(), settings.resilienceConfig(), effectivePlugins);
        if (logger != null) {
            agent.setLlmLogger(logger);
        }
        if (settings.systemPrompt() != null && !settings.systemPrompt().isBlank()) {
            agent.setSystemPrompt(settings.systemPrompt());
        }
        if (settings.structuredOutputConfig() != null) {
            agent.setStructuredOutputConfig(settings.structuredOutputConfig());
        }
        agent.setMaxToolIterations(settings.maxToolIterations());
        return agent;
    }

    /**
     * Builds an agent with tiered model support (simple, advanced, or routing).
     * <p>
     * Creates two model instances (simple/advanced) and optionally a {@link RoutingAgent}
     * when the default tier is {@link ModelTier#ROUTING}.
     * </p>
     *
     * @param useAdvancedModel unused (reserved); tier is determined by settings
     */
    public static Agent buildTieredAgent(AgentSettings settings, AgentConfig infra, Boolean useAdvancedModel) {
        var effectiveRegistry = infra.toolRegistry() != null ? infra.toolRegistry() : new ToolRegistry();
        var effectivePlugins = infra.plugins() != null ? infra.plugins() : List.<Plugin>of();

        var tc = settings.tieredConfig() != null ? settings.tieredConfig() : TieredModelConfig.fromEnv();
        var simpleModel = ModelFactory.createChatModel(ModelTier.SIMPLE, tc);
        var advancedModel = ModelFactory.createChatModel(ModelTier.ADVANCED, tc);
        var defaultTier = settings.modelTier() != null ? settings.modelTier() : tc.defaultTier();

        FileLlmLogger logger = null;
        ChatModel effectiveSimple = simpleModel;
        if (settings.llmLogPath() != null) {
            logger = new FileLlmLogger(settings.llmLogPath());
            effectiveSimple = new LoggingChatModel(simpleModel, logger);
        }

        Agent agent;
        if (defaultTier == ModelTier.ROUTING) {
            var router = new LlmRouter(simpleModel);
            agent = new RoutingAgent(effectiveSimple, advancedModel, router, effectiveRegistry, new DefaultToolExecutor(),
                infra.conversationManager(), infra.sessionManager(), infra.chatMemoryStore(), settings.resilienceConfig(), effectivePlugins);
        } else {
            agent = new Agent(effectiveSimple, effectiveRegistry, new DefaultToolExecutor(),
                infra.conversationManager(), infra.sessionManager(), infra.chatMemoryStore(), settings.resilienceConfig(), effectivePlugins);
            agent.setAdvancedModel(advancedModel);
            agent.setModelTier(defaultTier);
        }
        if (settings.systemPrompt() != null && !settings.systemPrompt().isBlank()) {
            agent.setSystemPrompt(settings.systemPrompt());
        }
        if (settings.structuredOutputConfig() != null) {
            agent.setStructuredOutputConfig(settings.structuredOutputConfig());
        }
        agent.setMaxToolIterations(settings.maxToolIterations());
        if (logger != null) {
            agent.setLlmLogger(logger);
        }
        return agent;
    }

    private static void wireCheckpointService(Agent agent, CheckpointService cpService) {
        for (var plugin : agent.getPlugins()) {
            if (plugin instanceof HITLPlugin hitl) {
                hitl.setCheckpointService(cpService);
                log.debug("Wired CheckpointService into HITLPlugin");
            }
        }
    }
}
