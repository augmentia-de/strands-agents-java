package de.augmentia.strandsagents.config;

import java.util.HashMap;
import java.util.Map;
import static de.augmentia.strandsagents.config.ConfigReader.*;

public record ChatModelConfig(
    ModelProviderType provider,
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries,
    Map<String, String> providerProperties,
    Boolean logRequests,
    Boolean logResponses
) {

    public ChatModelConfig {
        if (providerProperties == null) {
            providerProperties = Map.of();
        }
    }

    public static ChatModelConfig fromEnv(String prefix) {
        var provider = ModelProviderType.fromEnv(prefix);
        var props = new HashMap<String, String>();
        var ollamaUrl = get(prefix + "OLLAMA_BASE_URL", null);
        if (ollamaUrl != null) props.put("baseUrl", ollamaUrl);
        return new ChatModelConfig(
            provider,
            get(prefix + "API_KEY", null),
            get(prefix + "BASE_URL", null),
            get(prefix + "MODEL", null),
            parseDouble(get(prefix + "TEMPERATURE", null)),
            parseInt(get(prefix + "MAX_RETRIES", null)),
            Map.copyOf(props),
            parseBoolean(get(prefix + "LOG_REQUESTS", null)),
            parseBoolean(get(prefix + "LOG_RESPONSES", null))
        );
    }

    public static ChatModelConfig fromEnvWithFallback(String prefix, ChatModelConfig fallback) {
        var provider = ModelProviderType.fromEnv(prefix);
        if (!hasAny(prefix)) return fallback;
        var props = new HashMap<String, String>();
        var ollamaUrl = get(prefix + "OLLAMA_BASE_URL", fallback != null ? fallback.providerProperties().get("baseUrl") : null);
        if (ollamaUrl != null) props.put("baseUrl", ollamaUrl);
        return new ChatModelConfig(
            provider,
            get(prefix + "API_KEY", fallback != null ? fallback.apiKey() : null),
            get(prefix + "BASE_URL", fallback != null ? fallback.baseUrl() : null),
            get(prefix + "MODEL", fallback != null ? fallback.modelName() : null),
            parseDouble(get(prefix + "TEMPERATURE", fallback != null && fallback.temperature() != null ? fallback.temperature().toString() : null)),
            parseInt(get(prefix + "MAX_RETRIES", fallback != null && fallback.maxRetries() != null ? fallback.maxRetries().toString() : null)),
            Map.copyOf(props),
            parseBoolean(get(prefix + "LOG_REQUESTS", fallback != null && fallback.logRequests() != null ? fallback.logRequests().toString() : null)),
            parseBoolean(get(prefix + "LOG_RESPONSES", fallback != null && fallback.logResponses() != null ? fallback.logResponses().toString() : null))
        );
    }

    public static ChatModelConfig fromVault(Map<String, String> secrets, ChatModelConfig fallback) {
        var props = new HashMap<>(fallback.providerProperties());
        if (secrets.containsKey("ollama_base_url")) {
            props.put("baseUrl", secrets.get("ollama_base_url"));
        }
        return new ChatModelConfig(
            fallback.provider(),
            secrets.getOrDefault("api_key", fallback.apiKey()),
            secrets.getOrDefault("base_url", fallback.baseUrl()),
            secrets.getOrDefault("model", fallback.modelName()),
            parseDouble(secrets.get("temperature")),
            parseInt(secrets.get("max_retries")),
            Map.copyOf(props),
            parseBoolean(secrets.get("log_requests")),
            parseBoolean(secrets.get("log_responses"))
        );
    }

    public ChatModelConfig withApiKey(String apiKey) {
        return new ChatModelConfig(provider, apiKey, baseUrl, modelName, temperature, maxRetries, providerProperties, logRequests, logResponses);
    }

    public ChatModelConfig withModelName(String modelName) {
        return new ChatModelConfig(provider, apiKey, baseUrl, modelName, temperature, maxRetries, providerProperties, logRequests, logResponses);
    }

    public LlmConfig toLlmConfig() {
        return new LlmConfig(apiKey, baseUrl, modelName, temperature, maxRetries, logRequests, logResponses);
    }
}
