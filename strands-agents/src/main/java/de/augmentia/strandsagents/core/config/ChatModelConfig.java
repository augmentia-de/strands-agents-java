package de.augmentia.strandsagents.core.config;

import java.util.Map;

public record ChatModelConfig(
    ModelProviderType provider,
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries,
    String ollamaBaseUrl
) {

    public static ChatModelConfig fromEnv(String prefix) {
        var provider = ModelProviderType.fromEnv(prefix);
        return new ChatModelConfig(
            provider,
            envOrProperty(prefix + "API_KEY", null),
            envOrProperty(prefix + "BASE_URL", null),
            envOrProperty(prefix + "MODEL", null),
            parseDoubleOrNull(envOrProperty(prefix + "TEMPERATURE", null)),
            parseIntOrNull(envOrProperty(prefix + "MAX_RETRIES", null)),
            envOrProperty(prefix + "OLLAMA_BASE_URL", null)
        );
    }

    public static ChatModelConfig fromEnvWithFallback(String prefix, ChatModelConfig fallback) {
        var provider = ModelProviderType.fromEnv(prefix);
        if (!hasAny(prefix)) return fallback;
        return new ChatModelConfig(
            provider,
            envOrProperty(prefix + "API_KEY", fallback != null ? fallback.apiKey() : null),
            envOrProperty(prefix + "BASE_URL", fallback != null ? fallback.baseUrl() : null),
            envOrProperty(prefix + "MODEL", fallback != null ? fallback.modelName() : null),
            parseDoubleOrNull(envOrProperty(prefix + "TEMPERATURE", fallback != null && fallback.temperature() != null ? fallback.temperature().toString() : null)),
            parseIntOrNull(envOrProperty(prefix + "MAX_RETRIES", fallback != null && fallback.maxRetries() != null ? fallback.maxRetries().toString() : null)),
            envOrProperty(prefix + "OLLAMA_BASE_URL", fallback != null ? fallback.ollamaBaseUrl() : null)
        );
    }

    public static ChatModelConfig fromVault(Map<String, String> secrets, ChatModelConfig fallback) {
        return new ChatModelConfig(
            fallback.provider(),
            secrets.getOrDefault("api_key", fallback.apiKey()),
            secrets.getOrDefault("base_url", fallback.baseUrl()),
            secrets.getOrDefault("model", fallback.modelName()),
            parseDoubleOrNull(secrets.get("temperature")),
            parseIntOrNull(secrets.get("max_retries")),
            secrets.getOrDefault("ollama_base_url", fallback.ollamaBaseUrl())
        );
    }

    public ChatModelConfig withApiKey(String apiKey) {
        return new ChatModelConfig(provider, apiKey, baseUrl, modelName, temperature, maxRetries, ollamaBaseUrl);
    }

    public ChatModelConfig withModelName(String modelName) {
        return new ChatModelConfig(provider, apiKey, baseUrl, modelName, temperature, maxRetries, ollamaBaseUrl);
    }

    public LlmConfig toLlmConfig() {
        return new LlmConfig(apiKey, baseUrl, modelName, temperature, maxRetries);
    }

    private static boolean hasAny(String prefix) {
        return System.getenv(prefix + "PROVIDER") != null
            || System.getenv(prefix + "API_KEY") != null
            || System.getenv(prefix + "BASE_URL") != null
            || System.getenv(prefix + "MODEL") != null;
    }

    private static String envOrProperty(String key, String fallback) {
        var val = System.getProperty("vault." + key);
        if (val != null && !val.isBlank()) return val;
        val = System.getenv(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        return fallback;
    }

    private static Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
