package com.strands.agents.core;

import com.strands.agents.core.resilience.ResilienceConfig;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record AgentConfig(
    String name,
    String modelName,
    String systemPrompt,
    ToolRegistry toolRegistry,
    int maxIterations,
    ConversationManager conversationManager,
    SessionManager sessionManager,
    ResilienceConfig resilienceConfig,
    List<Plugin> plugins,
    Path skillsDir
) {
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    public static Builder builder() {
        return new Builder();
    }

    public StrandsAgent createAgent(ChatModel model) {
        var agent = new StrandsAgent(model, toolRegistry, new ToolExecutor(),
            conversationManager, sessionManager, resilienceConfig, plugins);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            agent.setSystemPrompt(systemPrompt);
        }
        return agent;
    }

    public StrandsAgent createAgent() {
        var model = ModelFactory.createOpenAiFromEnv();
        return createAgent(model);
    }

    public static class Builder {
        private String name = "unnamed";
        private String modelName = "openai/gpt-4o";
        private String systemPrompt = "";
        private ToolRegistry toolRegistry = new ToolRegistry();
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private ConversationManager conversationManager = null;
        private SessionManager sessionManager = null;
        private ResilienceConfig resilienceConfig = ResilienceConfig.DEFAULT;
        private List<Plugin> plugins = List.of();
        private Path skillsDir = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder conversationManager(ConversationManager conversationManager) { this.conversationManager = conversationManager; return this; }
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }
        public Builder resilienceConfig(ResilienceConfig resilienceConfig) { this.resilienceConfig = resilienceConfig; return this; }
        public Builder plugins(List<Plugin> plugins) { this.plugins = plugins; return this; }
        public Builder skillsDir(Path skillsDir) { this.skillsDir = skillsDir; return this; }

        public AgentConfig build() {
            return new AgentConfig(name, modelName, systemPrompt, toolRegistry, maxIterations,
                conversationManager, sessionManager, resilienceConfig, plugins, skillsDir);
        }
    }
}
