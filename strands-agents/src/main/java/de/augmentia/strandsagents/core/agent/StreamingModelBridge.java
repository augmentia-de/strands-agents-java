package de.augmentia.strandsagents.core.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * A bridge that adapts a {@link StreamingChatModel} to the {@link ChatModel} interface.
 * <p>
 * This class allows using streaming-capable models in places where a synchronous chat model
 * is expected. It handles the asynchronous nature of streaming by waiting for the complete
 * response while optionally forwarding incremental tokens to a consumer.
 * </p>
 */
public class StreamingModelBridge implements ChatModel {

    private final StreamingChatModel streamingModel;
    private final Consumer<String> tokenConsumer;
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    /**
     * Constructs a new StreamingModelBridge without a token consumer.
     *
     * @param streamingModel the underlying streaming chat model to use
     */
    public StreamingModelBridge(StreamingChatModel streamingModel) {
        this(streamingModel, null);
    }

    /**
     * Constructs a new StreamingModelBridge with a consumer for incremental tokens.
     *
     * @param streamingModel the underlying streaming chat model to use
     * @param tokenConsumer  a consumer that will receive each token as it is generated
     */
    public StreamingModelBridge(StreamingChatModel streamingModel, Consumer<String> tokenConsumer) {
        if (streamingModel == null) {
            throw new IllegalArgumentException("streamingModel cannot be null");
        }
        this.streamingModel = streamingModel;
        this.tokenConsumer = tokenConsumer;
    }

    /**
     * Executes a chat request by bridging the streaming response to a synchronous result.
     * <p>
     * This method blocks until the full response is received from the streaming model
     * or a timeout occurs (default 120 seconds).
     * </p>
     *
     * @param request the chat request containing messages and configuration
     * @return the complete chat response
     * @throws RuntimeException if the request fails, times out, or is interrupted
     */
    @Override
    public ChatResponse chat(ChatRequest request) {
        var future = new CompletableFuture<ChatResponse>();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                // If you ever need to log tokens or pipe them to a UI, this handles it cleanly
                if (tokenConsumer != null && token != null) {
                    tokenConsumer.accept(token);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // The response parameter passed here already contains the fully assembled text/tokens
                future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Streaming request timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds", e);
        } catch (InterruptedException e) {
            // CRITICAL: Restore the interrupted status so upstream workers/thread-pools know to halt
            Thread.currentThread().interrupt();
            throw new RuntimeException("Streaming request was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Streaming chat failed", cause != null ? cause : e);
        }
    }
}