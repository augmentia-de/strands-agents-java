package de.augmentia.strandsagents.core;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

public class MockStreamingChatModel implements StreamingChatModel {

    private final String responseTemplate;
    private final long tokenDelayMs;

    public MockStreamingChatModel() {
        this("Mock antwortet: %s", 0);
    }

    public MockStreamingChatModel(String responseTemplate) {
        this(responseTemplate, 0);
    }

    public MockStreamingChatModel(String responseTemplate, long tokenDelayMs) {
        this.responseTemplate = responseTemplate;
        this.tokenDelayMs = tokenDelayMs;
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        var messages = request.messages();
        String text;
        if (messages.isEmpty()) {
            text = "";
        } else {
            var lastMessage = messages.get(messages.size() - 1);
            text = lastMessage instanceof UserMessage um
                ? um.singleText() : lastMessage.toString();
        }
        var responseText = responseTemplate.formatted(text);

        for (int i = 0; i < responseText.length(); i++) {
            if (tokenDelayMs > 0) {
                try {
                    Thread.sleep(tokenDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    handler.onError(e);
                    return;
                }
            }
            handler.onPartialResponse(String.valueOf(responseText.charAt(i)));
        }

        handler.onCompleteResponse(ChatResponse.builder()
            .aiMessage(AiMessage.from(responseText))
            .tokenUsage(new TokenUsage(10, responseText.length()))
            .finishReason(FinishReason.STOP)
            .build());
    }
}
