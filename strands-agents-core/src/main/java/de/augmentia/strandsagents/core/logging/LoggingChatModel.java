package de.augmentia.strandsagents.core.logging;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

public class LoggingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final FileLlmLogger logger;

    public LoggingChatModel(ChatModel delegate, FileLlmLogger logger) {
        this.delegate = delegate;
        this.logger = logger;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        var start = System.nanoTime();
        try {
            var response = delegate.chat(request);
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            logger.log(request, response, durationMs);
            return response;
        } catch (RuntimeException e) {
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            logger.log(request, errorResponse(e), durationMs);
            throw e;
        }
    }

    private static ChatResponse errorResponse(RuntimeException e) {
        return ChatResponse.builder()
            .aiMessage(dev.langchain4j.data.message.AiMessage.from(
                "[LLM error: " + e.getMessage() + "]"))
            .build();
    }
}
