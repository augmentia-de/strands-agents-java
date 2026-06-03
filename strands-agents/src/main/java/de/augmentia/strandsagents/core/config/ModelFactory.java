package de.augmentia.strandsagents.core.config;

import de.augmentia.strandsagents.core.secret.SecretProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

public class ModelFactory {

    public static ChatModel createOpenAiFromEnv(String api_key) {
        LlmConfig config = LlmConfig.fromEnv();
        if (api_key!=null) config = new LlmConfig(api_key, config.baseUrl(), config.modelName(), config.temperature(), config.maxRetries());
        return createOpenAi(config);
    }
    public static ChatModel createOpenAiFromEnv() {
        LlmConfig config = LlmConfig.fromEnv();
        return createOpenAi(config);
    }

    public static ChatModel createOpenAi(LlmConfig config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(config.apiKey());

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            builder.baseUrl(config.baseUrl());
        }
        var modelName = config.modelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = "no-model";
        }
        builder.modelName(modelName);
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }
        if (config.maxRetries() != null) {
            builder.maxRetries(config.maxRetries());
        }

        return builder.build();
    }

    public static StreamingChatModel createOpenAiStreamingFromEnv(String api_key) {
        LlmConfig config = LlmConfig.fromEnv();
        if (api_key!=null) config = new LlmConfig(api_key, config.baseUrl(), config.modelName(), config.temperature(), config.maxRetries());
        return createOpenAiStreaming(config);
    }
    public static StreamingChatModel createOpenAiStreaming(LlmConfig config) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(config.apiKey());

        if (config.baseUrl() != null && !config.baseUrl().isBlank()) {
            builder.baseUrl(config.baseUrl());
        }
        var modelName = config.modelName();
        if (modelName == null || modelName.isBlank()) {
            modelName = "gpt-4o";
        }
        builder.modelName(modelName);
        if (config.temperature() != null) {
            builder.temperature(config.temperature());
        }

        return builder.build();
    }
}
