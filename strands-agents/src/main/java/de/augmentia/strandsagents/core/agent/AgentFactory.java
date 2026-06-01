package de.augmentia.strandsagents.core.agent;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.config.StrandsAgentConfig;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointHook;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import de.augmentia.strandsagents.core.tools.local.HttpTool;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import de.augmentia.strandsagents.skills.Skill;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
            var skillsPlugin = new de.augmentia.strandsagents.skills.AgentSkillsPlugin(
                skills, initialSkills != null ? initialSkills : List.of());
            skillsPlugin.setSkillSearchEnabled(skillSearchEnabled);
            plugins.add(skillsPlugin);
        }
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        return plugins;
    }

    public static List<Plugin> buildPlugins() {
        return buildPlugins(List.of(), List.of(), false);
    }

    public static Agent createAgent(ChatModel model, ToolRegistry tools,
                                     SessionManager sessionManager,
                                     CheckpointService cpService,
                                     List<Plugin> plugins) {
        var agent = new Agent(model, tools, new de.augmentia.strandsagents.core.ToolExecutor(),
            null, sessionManager, null, plugins);
        if (cpService != null) {
            var cpHook = new CheckpointHook(cpService);
            agent.setCheckpointService(cpService);
            agent.addHook(cpHook);
            cpHook.setAgent(agent);
        }
        return agent;
    }
}
