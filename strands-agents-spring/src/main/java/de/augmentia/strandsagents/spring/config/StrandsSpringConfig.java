package de.augmentia.strandsagents.spring.config;

import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.config.LlmConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.tools.local.WebFetchTool;
import de.augmentia.strandsagents.core.tools.local.WebSearchTool;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StrandsSpringConfig {

    @Bean
    public StreamingChatModel streamingChatModel() {
        var config = LlmConfig.fromEnv();
        return ModelFactory.createOpenAiStreaming(config);
    }

    @Bean
    public ToolRegistry toolRegistry() {
        var registry = new ToolRegistry();
        registry.register(new WebSearchTool());
        registry.register(new WebFetchTool());
        return registry;
    }

    @Bean
    public ToolExecutor toolExecutor() {
        return new DefaultToolExecutor();
    }

    @Bean
    public ConversationManager conversationManager() {
        return new SlidingWindowConversationManager(30);
    }

    @Bean
    public SessionManager sessionManager() {
        return new FileSessionManager(Path.of(".sessions"));
    }

    @Bean
    public ResilienceConfig resilienceConfig() {
        return ResilienceConfig.DEFAULT;
    }
}
