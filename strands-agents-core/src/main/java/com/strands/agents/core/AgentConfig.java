package com.strands.agents.core;

import java.util.List;
import java.util.Map;

public record AgentConfig(
    String name,
    String modelName,
    List<String> toolClassNames,
    int maxIterations,
    Map<String, String> routes,
    ConversationManager conversationManager,
    SessionManager sessionManager
) {
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "unnamed";
        private String modelName = "openai/gpt-4o";
        private List<String> toolClassNames = List.of();
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private Map<String, String> routes = Map.of();
        private ConversationManager conversationManager = null;
        private SessionManager sessionManager = null;

        public Builder name(String name) { this.name = name; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder toolClassNames(List<String> toolClassNames) { this.toolClassNames = toolClassNames; return this; }
        public Builder maxIterations(int maxIterations) { this.maxIterations = maxIterations; return this; }
        public Builder routes(Map<String, String> routes) { this.routes = routes; return this; }
        public Builder conversationManager(ConversationManager conversationManager) { this.conversationManager = conversationManager; return this; }
        public Builder sessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; return this; }

        public AgentConfig build() {
            return new AgentConfig(name, modelName, toolClassNames, maxIterations, routes, conversationManager, sessionManager);
        }
    }
}
