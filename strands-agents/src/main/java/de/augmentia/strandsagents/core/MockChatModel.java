package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.prompt.PromptRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * A mock implementation of {@link ChatModel} for testing purposes.
 * <p>
 * This model simply echoes back the user's last message using a configurable template,
 * without requiring an actual LLM backend.
 * </p>
 */
public class MockChatModel implements ChatModel {

    private final String responseTemplate;

    /**
     * Constructs a default MockChatModel that uses the configured prompt template.
     */
    public MockChatModel() {
        this(PromptRegistry.getOrDefault("mock_chat_model.template", "Mock response: %s"));
    }

    /**
     * Constructs a MockChatModel with a custom response template.
     *
     * @param responseTemplate a format string (e.g., "Hello %s") used to generate responses
     */
    public MockChatModel(String responseTemplate) {
        this.responseTemplate = responseTemplate;
    }

    /**
     * Executes a chat request by echoing the last user message.
     *
     * @param request the chat request
     * @return a chat response containing the formatted template
     */
    @Override
    public ChatResponse chat(ChatRequest request) {
        var messages = request.messages();
        if (messages.isEmpty()) {
            return ChatResponse.builder()
                .aiMessage(AiMessage.from(""))
                .tokenUsage(new TokenUsage(0, 0))
                .finishReason(FinishReason.STOP)
                .build();
        }
        var lastMessage = messages.get(messages.size() - 1);
        var text = lastMessage instanceof UserMessage um
            ? um.singleText() : lastMessage.toString();
        var responseText = responseTemplate.formatted(text);
        return ChatResponse.builder()
            .aiMessage(AiMessage.from(responseText))
            .tokenUsage(new TokenUsage(10, responseText.length()))
            .finishReason(FinishReason.STOP)
            .build();
    }
}
