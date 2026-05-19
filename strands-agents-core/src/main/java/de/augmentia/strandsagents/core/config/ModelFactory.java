package de.augmentia.strandsagents.core.config;

import de.augmentia.strandsagents.core.secret.SecretProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ModelFactory {

    public static ChatModel createOpenAiFromEnv() {
        return createOpenAi(LlmConfig.fromEnv());
    }

    public static ChatModel createOpenAiFromVault(SecretProvider vault, String path) {
        return createOpenAi(LlmConfig.fromVault(vault, path));
    }

    public static ChatModel createOpenAi(LlmConfig config) {
        var builder = OpenAiChatModel.builder()
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
        if (config.maxRetries() != null) {
            builder.maxRetries(config.maxRetries());
        }

        return builder.build();
    }
}
