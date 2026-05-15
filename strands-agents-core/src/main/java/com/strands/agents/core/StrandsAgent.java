package com.strands.agents.core;

import com.strands.agents.core.internal.ChatMessageConverter;
import com.strands.agents.core.model.agent.*;
import com.strands.agents.core.model.message.Message;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.UUID;

public class StrandsAgent implements Agent {

    private final ChatModel model;
    private final ChatMemory chatMemory;
    private final String sessionId;
    private final int maxMessages;

    public StrandsAgent(ChatModel model) {
        this(model, 10);
    }

    public StrandsAgent(ChatModel model, int maxMessages) {
        this.model = model;
        this.maxMessages = maxMessages;
        this.chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(maxMessages)
            .build();
        this.sessionId = UUID.randomUUID().toString();
    }

    @Override
    public AgentResult execute(String prompt) {
        var start = System.nanoTime();

        chatMemory.add(UserMessage.from(prompt));
        ChatResponse response = model.chat(chatMemory.messages());
        AiMessage aiMessage = response.aiMessage();
        chatMemory.add(aiMessage);

        var durationMs = (System.nanoTime() - start) / 1_000_000;

        List<Message> generatedMessages = ChatMessageConverter.toDomainMessages(
            chatMemory.messages()
        );

        int inputTokens = response.tokenUsage() != null
            ? response.tokenUsage().inputTokenCount() : 0;
        int outputTokens = response.tokenUsage() != null
            ? response.tokenUsage().outputTokenCount() : 0;

        return new AgentResult(
            sessionId,
            aiMessage.text(),
            generatedMessages,
            new ExecutionMetrics(durationMs, inputTokens, outputTokens, 0),
            StopReason.COMPLETED
        );
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public String getSessionId() {
        return sessionId;
    }
}
