package de.augmentia.strandsagents.core.agent;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.event.TokenEvent;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.util.List;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class StreamingAgent extends Agent {

    private final StreamingChatModel streamingModel;
    private volatile Consumer<String> currentTokenConsumer;

    public StreamingAgent(StreamingChatModel streamingModel) {
        super(new StreamingModelBridge(streamingModel));
        this.streamingModel = streamingModel;
    }

    public StreamingAgent(StreamingChatModel streamingModel, ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor) {
        super(new StreamingModelBridge(streamingModel), toolRegistry, toolExecutor);
        this.streamingModel = streamingModel;
    }

    public StreamingAgent(StreamingChatModel streamingModel, ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor, ConversationManager conversationManager) {
        super(new StreamingModelBridge(streamingModel), toolRegistry, toolExecutor, conversationManager);
        this.streamingModel = streamingModel;
    }

    public StreamingAgent(StreamingChatModel streamingModel, ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor, ConversationManager conversationManager,
                          SessionManager sessionManager) {
        super(new StreamingModelBridge(streamingModel), toolRegistry, toolExecutor,
            conversationManager, sessionManager);
        this.streamingModel = streamingModel;
    }

    public StreamingAgent(StreamingChatModel streamingModel, ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor, ConversationManager conversationManager,
                          SessionManager sessionManager, ResilienceConfig resilienceConfig) {
        super(new StreamingModelBridge(streamingModel), toolRegistry, toolExecutor,
            conversationManager, sessionManager, resilienceConfig);
        this.streamingModel = streamingModel;
    }

    public StreamingAgent(StreamingChatModel streamingModel, ToolRegistry toolRegistry,
                          ToolExecutor toolExecutor, ConversationManager conversationManager,
                          SessionManager sessionManager, ResilienceConfig resilienceConfig,
                          List<Plugin> plugins) {
        super(new StreamingModelBridge(streamingModel), toolRegistry, toolExecutor,
            conversationManager, sessionManager, resilienceConfig, plugins);
        this.streamingModel = streamingModel;
    }

    public AgentResult executeStreaming(String prompt, Consumer<String> tokenHandler) {
        currentTokenConsumer = tokenHandler;
        try {
            return execute(prompt);
        } finally {
            currentTokenConsumer = null;
        }
    }

    public CompletableFuture<AgentResult> executeStreamingAsync(String prompt,
                                                                  Consumer<String> tokenHandler) {
        return CompletableFuture.supplyAsync(() -> executeStreaming(prompt, tokenHandler),
            Agent.VIRTUAL_EXECUTOR);
    }

    @Override
    protected ChatResponse doChat(ChatRequest request) {
        var consumer = currentTokenConsumer;
        var bridge = new StreamingModelBridge(streamingModel, token -> {
            fire(new TokenEvent(getSessionId(), Instant.now(), token));
            if (consumer != null) {
                consumer.accept(token);
            }
        });
        return bridge.chat(request);
    }

    public StreamingChatModel getStreamingModel() {
        return streamingModel;
    }
}
