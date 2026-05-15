package com.strands.agents.core;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ModelFactory {

    public static ChatModel createOpenAiFromEnv() {
        return createOpenAi(LlmConfig.fromEnv());
    }

    public static ChatModel createOpenAi(LlmConfig config) {
        var builder = OpenAiChatModel.builder()
            .apiKey(config.apiKey());

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.modelName() != null && !config.modelName().isBlank()) {
            builder.modelName(config.modelName());
        }
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.maxRetries() != null) {
            builder.maxRetries(config.maxRetries());
        }

        return builder.build();
    }
}
