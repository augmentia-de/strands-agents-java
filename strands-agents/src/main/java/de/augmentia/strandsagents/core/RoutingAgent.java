package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.routing.LlmRouter;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * An agent that routes requests between a simple and an advanced model tier.
 * <p>
 * Uses an {@link LlmRouter} or LLM-based classification to decide which model
 * tier should handle a given request, enabling cost-optimised execution.
 * </p>
 */
public class RoutingAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(RoutingAgent.class);

    private final ChatModel simpleModel;
    private final ChatModel advancedModel;
    private final LlmRouter router;
    private volatile ModelTier resolvedTier;

    /**
     * Constructs a RoutingAgent without an explicit router (uses LLM-based classification).
     */
    public RoutingAgent(ChatModel simpleModel, ChatModel advancedModel,
                        ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager, SessionManager sessionManager,
                        ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
                        List<Plugin> plugins) {
        this(simpleModel, advancedModel, null, toolRegistry, toolExecutor, conversationManager,
            sessionManager, chatMemoryStore, resilienceConfig, plugins);
    }

    /**
     * Constructs a RoutingAgent with an explicit {@link LlmRouter}.
     */
    public RoutingAgent(ChatModel simpleModel, ChatModel advancedModel, LlmRouter router,
                        ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager, SessionManager sessionManager,
                        ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
                        List<Plugin> plugins) {
        super(simpleModel, toolRegistry, toolExecutor, conversationManager, sessionManager,
            chatMemoryStore, resilienceConfig, plugins);
        this.simpleModel = simpleModel;
        this.advancedModel = advancedModel;
        this.router = router;
        this.resolvedTier = ModelTier.SIMPLE;
        setAdvancedModel(advancedModel);
        setModelTier(ModelTier.ROUTING);
    }

    /**
     * Determines whether a user goal should use the SIMPLE or ADVANCED model tier.
     *
     * @return the resolved model tier
     */
    public ModelTier resolveRoutingTier(String userGoal) {
        if (router != null) {
            var result = router.classify(userGoal, List.of("SIMPLE", "ADVANCED"));
            if ("ADVANCED".equalsIgnoreCase(result.topic())) {
                resolvedTier = ModelTier.ADVANCED;
            } else {
                resolvedTier = ModelTier.SIMPLE;
            }
            log.debug("Routing resolved tier={} (via router) for goal='{}'", resolvedTier, truncate(userGoal));
            return resolvedTier;
        }

        var prompt = PromptRegistry.get("routing_agent.classifier", userGoal);
        var request = ChatRequest.builder()
            .messages(List.of(
                SystemMessage.from(PromptRegistry.get("routing_agent.system")),
                UserMessage.from(prompt)
            ))
            .build();
        try {
            var response = simpleModel.chat(request);
            var text = response.aiMessage().text();
            if (text != null) {
                var trimmed = text.strip().toLowerCase();
                if (trimmed.contains("advanced")) {
                    resolvedTier = ModelTier.ADVANCED;
                } else {
                    resolvedTier = ModelTier.SIMPLE;
                }
            }
        } catch (Exception e) {
            log.warn("Routing analysis failed, defaulting to simple: {}", e.getMessage());
            resolvedTier = ModelTier.SIMPLE;
        }
        log.debug("Routing resolved tier={} for goal='{}'", resolvedTier, truncate(userGoal));
        return resolvedTier;
    }

    public ModelTier getResolvedTier() {
        return resolvedTier;
    }

    /**
     * Activates the resolved tier by switching to the appropriate model.
     */
    public void applyRouting() {
        if (resolvedTier == ModelTier.ADVANCED && advancedModel != null) {
            switchTier(ModelTier.ADVANCED);
        } else {
            switchTier(ModelTier.SIMPLE);
        }
        log.debug("Routing applied: {}", resolvedTier);
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= 2000 ? s : s.substring(0, 2000) + "...";
    }
}
