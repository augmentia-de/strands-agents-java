package de.augmentia.strandsagents.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncToStreamingBridge implements StreamingChatModel {

    private final ChatModel delegate;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SyncToStreamingBridge(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        executor.submit(() -> {
            try {
                var response = delegate.chat(request);
                var aiMessage = response.aiMessage();
                if (aiMessage != null && aiMessage.text() != null) {
                    for (int i = 0; i < aiMessage.text().length(); i++) {
                        handler.onPartialResponse(String.valueOf(aiMessage.text().charAt(i)));
                    }
                }
                handler.onCompleteResponse(response);
            } catch (Exception e) {
                handler.onError(e);
            }
        });
    }
}
