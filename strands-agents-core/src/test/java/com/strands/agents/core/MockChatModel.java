package com.strands.agents.core;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

public class MockChatModel implements ChatModel {

    private final String responseTemplate;

    public MockChatModel() {
        this("Mock antwortet: %s");
    }

    public MockChatModel(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }

    @Override
    public ChatResponse chat(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(""))
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.STOP)
                .build();
        }
        var lastMessage = messages.get(messages.size() - 1);
        var text = lastMessage instanceof dev.langchain4j.data.message.UserMessage um
            ? um.singleText() : lastMessage.toString();
        var responseText = responseTemplate.formatted(text);
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(responseText))
            .tokenUsage(new TokenUsage(10, responseText.length()))
            .finishReason(FinishReason.STOP)
            .build();
    }
}
