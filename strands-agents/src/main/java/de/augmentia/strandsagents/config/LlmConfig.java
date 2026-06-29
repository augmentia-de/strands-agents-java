package de.augmentia.strandsagents.config;

import de.augmentia.strandsagents.features.secrets.SecretProvider;
import static de.augmentia.strandsagents.config.ConfigReader.*;

public record LlmConfig(
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries,
    Boolean logRequests,
    Boolean logResponses
) {

    public static LlmConfig fromEnv() {
        return new LlmConfig(
            get("OPENAI_API_KEY"),
            get("OPENAI_BASE_URL"),
            get("OPENAI_MODEL"),
            parseDouble(get("LLM_TEMPERATURE")),
            parseInt(get("LLM_MAX_RETRIES")),
            parseBoolean(get("LLM_LOG_REQUESTS")),
            parseBoolean(get("LLM_LOG_RESPONSES"))
        );
    }

    public static LlmConfig fromEnv(String modelName) {
        var env = fromEnv();
        return new LlmConfig(
            env.apiKey(),
            env.baseUrl(),
            modelName != null && !modelName.isBlank() ? modelName : env.modelName(),
            env.temperature(),
            env.maxRetries(),
            env.logRequests(),
            env.logResponses()
        );
    }

    public static LlmConfig fromVault(SecretProvider vault, String path) {
        var secrets = vault.getSecrets(path);
        return new LlmConfig(
            secrets.get("api_key"),
            secrets.getOrDefault("base_url", get("OPENAI_BASE_URL")),
            secrets.getOrDefault("model", get("OPENAI_MODEL")),
            parseDouble(secrets.get("temperature")),
            parseInt(secrets.get("max_retries")),
            parseBoolean(secrets.get("log_requests")),
            parseBoolean(secrets.get("log_responses"))
        );
    }
}
