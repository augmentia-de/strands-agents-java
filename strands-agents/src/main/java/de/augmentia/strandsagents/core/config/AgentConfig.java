package de.augmentia.strandsagents.core.config;


import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;
import dev.langchain4j.model.chat.ChatModel;


import java.nio.file.Path;
import java.util.List;

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
    Path skillsDir,
    List<String> initialSkills,
    StructuredOutputConfig structuredOutputConfig,
    Path llmLogPath
) {
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    public static Builder builder() {
        return new Builder();
    }

    public Agent createAgent(ChatModel model) {
        var effectiveRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        var effectivePlugins = plugins != null ? plugins : List.<Plugin>of();

        if (llmLogPath != null) {
            var logger = new FileLlmLogger(llmLogPath);
            model = new LoggingChatModel(model, logger);
            Runtime.getRuntime().addShutdownHook(new Thread(logger::close));
        }

        var agent = new Agent(model, effectiveRegistry, new ToolExecutor(),
            conversationManager, sessionManager, resilienceConfig, effectivePlugins);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            agent.setSystemPrompt(systemPrompt);
        }
        if (structuredOutputConfig != null) {
            agent.setStructuredOutputConfig(structuredOutputConfig);
        }
        return agent;
    }

    public Agent createAgent() {
        var model = ModelFactory.createOpenAiFromEnv();
        return createAgent(model);
    }

    public static class Builder {
        private String name = "unnamed";
        private String modelName = "gpt-4o";
        private String systemPrompt = "";
        private ToolRegistry toolRegistry = new ToolRegistry();
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private ConversationManager conversationManager = null;
        private SessionManager sessionManager = null;
        private ResilienceConfig resilienceConfig = ResilienceConfig.DEFAULT;
        private List<Plugin> plugins = List.of();
        private Path skillsDir = null;
        private List<String> initialSkills = List.of();
        private StructuredOutputConfig structuredOutputConfig = null;
        private Path llmLogPath = null;

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
        public Builder initialSkills(List<String> initialSkills) { this.initialSkills = initialSkills; return this; }
        public Builder structuredOutputConfig(StructuredOutputConfig config) { this.structuredOutputConfig = config; return this; }
        public Builder structuredOutputModel(Class<?> modelClass) { this.structuredOutputConfig = StructuredOutputConfig.staticModel(modelClass); return this; }
        public Builder structuredOutputSchema(String jsonSchema) { this.structuredOutputConfig = StructuredOutputConfig.dynamicSchema(jsonSchema); return this; }
        public Builder logLlmCalls(Path path) { this.llmLogPath = path; return this; }

        public AgentConfig build() {
            return new AgentConfig(name, modelName, systemPrompt, toolRegistry, maxIterations,
                conversationManager, sessionManager, resilienceConfig, plugins, skillsDir,
                initialSkills, structuredOutputConfig, llmLogPath);
        }
    }
}
