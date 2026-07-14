package de.augmentia.strandsagents.config;

import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;

/**
 * Pure infrastructure wiring record — holds only services and components,
 * never pure data. Use {@link AgentSettings} for domain configuration.
 */
public record AgentConfig(
    ToolRegistry toolRegistry,
    ConversationManager conversationManager,
    SessionManager sessionManager,
    ChatMemoryStore chatMemoryStore,
    List<Plugin> plugins
) {
    public static final AgentConfig EMPTY = new AgentConfig(new ToolRegistry(), null, null, null, List.of());

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link AgentConfig} with defaults for tool registry and plugins.
     */
    public static class Builder {
        private ToolRegistry toolRegistry = new ToolRegistry();
        private ConversationManager conversationManager = null;
        private SessionManager sessionManager = null;
        private ChatMemoryStore chatMemoryStore = null;
        private List<Plugin> plugins = List.of();

        Builder() {}

        public Builder toolRegistry(ToolRegistry toolRegistry) { this.toolRegistry = toolRegistry; return this; }
        public Builder conversationManager(ConversationManager conversationManager) { this.conversationManager = conversationManager; return this; }
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }
        public Builder chatMemoryStore(ChatMemoryStore chatMemoryStore) { this.chatMemoryStore = chatMemoryStore; return this; }
        public Builder plugins(List<Plugin> plugins) { this.plugins = plugins; return this; }

        public AgentConfig build() {
            return new AgentConfig(toolRegistry, conversationManager, sessionManager, chatMemoryStore, plugins);
        }
    }
}