package de.augmentia.strandsagents.core;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class StreamingModelBridge implements ChatModel {

    private final StreamingChatModel streamingModel;
    private final Consumer<String> tokenConsumer;

    public StreamingModelBridge(StreamingChatModel streamingModel) {
        this(streamingModel, null);
    }

    public StreamingModelBridge(StreamingChatModel streamingModel, Consumer<String> tokenConsumer) {
        this.streamingModel = streamingModel;
        this.tokenConsumer = tokenConsumer;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var future = new CompletableFuture<ChatResponse>();

        streamingModel.chat(request, new StreamingChatResponseHandler() {
            private final StringBuilder partialResponse = new StringBuilder();

            @Override
            public void onPartialResponse(String token) {
                partialResponse.append(token);
                if (tokenConsumer != null) {
                    tokenConsumer.accept(token);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                future.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get(120, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Streaming timeout", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Streaming chat failed", e);
        }
    }
}
