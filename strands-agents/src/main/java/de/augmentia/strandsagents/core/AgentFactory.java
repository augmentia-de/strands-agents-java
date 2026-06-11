package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.config.StrandsAgentConfig;
import de.augmentia.strandsagents.features.conversation.ConversationManager;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.features.hitl.HITLPlugin;
import de.augmentia.strandsagents.features.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.features.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.features.hitl.checkpoint.EmailChannel;
import de.augmentia.strandsagents.features.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.features.tools.ListToolsTool;
import de.augmentia.strandsagents.features.tools.HttpTool;
import de.augmentia.strandsagents.features.sessions.FileSessionManager;
import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.features.skills.Skill;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    private AgentFactory() {}

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

    public static CheckpointService createCheckpointService(StrandsAgentConfig config) {
        return createCheckpointService(config, null);
    }

    public static SessionManager createSessionManager(Path sessionDir) {
        try {
            Files.createDirectories(sessionDir);
        } catch (Exception ignored) {}
        return new FileSessionManager(sessionDir);
    }

    public static ConversationManager createConversationManager(int windowSize) {
        return new SlidingWindowConversationManager(windowSize);
    }

    public static List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills,
                                             boolean skillSearchEnabled) {
        var plugins = new ArrayList<Plugin>();
        if (skills != null && !skills.isEmpty()) {
            var skillsPlugin = new de.augmentia.strandsagents.features.skills.AgentSkillsPlugin(
                skills, initialSkills != null ? initialSkills : List.of());
            skillsPlugin.setSkillSearchEnabled(skillSearchEnabled);
            plugins.add(skillsPlugin);
        }
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        sortPlugins(plugins);
        return List.copyOf(plugins);
    }

    public static List<Plugin> buildPlugins() {
        return buildPlugins(List.of(), List.of(), false);
    }

    public static List<Plugin> sortPlugins(List<Plugin> plugins) {
        plugins.sort(Comparator.comparingInt(Plugin::order));
        return plugins;
    }

    public static Agent createAgent(ChatModel model, ToolRegistry tools,
                                     SessionManager sessionManager,
                                     CheckpointService cpService,
                                     List<Plugin> plugins) {
        var agent = new Agent(model, tools, new ToolExecutor(),
            null, sessionManager, null, plugins);
        if (cpService != null) {
            agent.setCheckpointService(cpService);
            wireCheckpointService(agent, cpService);
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
