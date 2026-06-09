package de.augmentia.strandsagents.core.config;

import de.augmentia.strandsagents.core.secret.SecretProvider;
import static de.augmentia.strandsagents.core.config.ConfigReader.*;

public record LlmConfig(
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries
) {

    public static LlmConfig fromEnv() {
        return new LlmConfig(
            get("OPENAI_API_KEY"),
            get("OPENAI_BASE_URL"),
            get("OPENAI_MODEL"),
            parseDouble(get("LLM_TEMPERATURE")),
            parseInt(get("LLM_MAX_RETRIES"))
        );
    }

    public static LlmConfig fromEnv(String modelName) {
        var env = fromEnv();
        return new LlmConfig(
            env.apiKey(),
            env.baseUrl(),
            modelName != null && !modelName.isBlank() ? modelName : env.modelName(),
            env.temperature(),
            env.maxRetries()
        );
    }

    public static LlmConfig fromVault(SecretProvider vault, String path) {
        var secrets = vault.getSecrets(path);
        return new LlmConfig(
            secrets.get("api_key"),
            secrets.getOrDefault("base_url", get("OPENAI_BASE_URL")),
            secrets.getOrDefault("model", get("OPENAI_MODEL")),
            parseDouble(secrets.get("temperature")),
            parseInt(secrets.get("max_retries"))
        );
    }
}
