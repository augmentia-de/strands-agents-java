package de.augmentia.strandsagents.config;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.CheckpointStore;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.config.secrets.SecretProvider;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StrandsContext {

    private final ChatModel model;
    private final ChatModel advancedModel;
    private final String modelName;
    private final ToolRegistry toolRegistry;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private final ChatMemoryStore chatMemoryStore;
    private final ResilienceConfig resilienceConfig;
    private final List<Plugin> plugins;
    private final String systemPrompt;
    private final StructuredOutputConfig structuredOutputConfig;
    private final CheckpointStore checkpointStore;
    private final CheckpointService checkpointService;
    private final SecretProvider secretProvider;
    private final Path llmLogPath;

    private StrandsContext(Builder builder) {
        this.model = builder.model;
        this.advancedModel = builder.advancedModel;
        this.modelName = builder.modelName;
        this.toolRegistry = builder.toolRegistry != null ? builder.toolRegistry : new ToolRegistry();
        this.conversationManager = builder.conversationManager;
        this.sessionManager = builder.sessionManager;
        this.chatMemoryStore = builder.chatMemoryStore;
        this.resilienceConfig = builder.resilienceConfig;
        this.plugins = builder.plugins != null ? List.copyOf(builder.plugins) : List.of();
        this.systemPrompt = builder.systemPrompt;
        this.structuredOutputConfig = builder.structuredOutputConfig;
        this.checkpointStore = builder.checkpointStore;
        this.checkpointService = builder.checkpointService;
        this.secretProvider = builder.secretProvider;
        this.llmLogPath = builder.llmLogPath;
    }

    public SecretProvider getSecretProvider() {
        return secretProvider;
    }

    public CheckpointStore getCheckpointStore() {
        return checkpointStore;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static StrandsContext fromConfig(StrandsAgentConfig config) {
        if (config.llmLogPath() != null) {
            return builder()
                .llmLogPath(Path.of(config.llmLogPath()))
                .build();
        }
        return builder().build();
    }

    public static class Builder {
        private ChatModel model;
        private ChatModel advancedModel;
        private String modelName;
        private ToolRegistry toolRegistry;
        private ConversationManager conversationManager;
        private SessionManager sessionManager;
        private ChatMemoryStore chatMemoryStore;
        private ResilienceConfig resilienceConfig;
        private final List<Plugin> plugins = new ArrayList<>();
        private String systemPrompt;
        private StructuredOutputConfig structuredOutputConfig;
        private CheckpointStore checkpointStore;
        private CheckpointService checkpointService;
        private SecretProvider secretProvider;
        private Path llmLogPath;

        Builder() {}

        public Builder model(ChatModel model) { this.model = model; return this; }
        public Builder advancedModel(ChatModel advancedModel) { this.advancedModel = advancedModel; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder conversationManager(ConversationManager conversationManager) { this.conversationManager = conversationManager; return this; }
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }
        public Builder chatMemoryStore(ChatMemoryStore chatMemoryStore) { this.chatMemoryStore = chatMemoryStore; return this; }
        public Builder resilienceConfig(ResilienceConfig resilienceConfig) { this.resilienceConfig = resilienceConfig; return this; }
        public Builder addPlugin(Plugin plugin) { this.plugins.add(plugin); return this; }
        public Builder plugins(List<Plugin> plugins) { this.plugins.addAll(plugins); return this; }
        public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
        public Builder structuredOutputConfig(StructuredOutputConfig config) { this.structuredOutputConfig = config; return this; }
        public Builder checkpointStore(CheckpointStore store) { this.checkpointStore = store; return this; }
        public Builder checkpointService(CheckpointService service) { this.checkpointService = service; return this; }
        public Builder secretProvider(SecretProvider secretProvider) { this.secretProvider = secretProvider; return this; }
        public Builder llmLogPath(Path llmLogPath) { this.llmLogPath = llmLogPath; return this; }

        public StrandsContext build() {
            if (model == null && modelName == null) {
                throw new IllegalStateException("ChatModel or modelName is required to build a StrandsContext");
            }
            return new StrandsContext(this);
        }
    }
}
