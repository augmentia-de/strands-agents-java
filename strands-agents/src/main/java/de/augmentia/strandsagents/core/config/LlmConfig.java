package de.augmentia.strandsagents.core.config;

import de.augmentia.strandsagents.core.secret.SecretProvider;

public record LlmConfig(
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries
) {

    public static LlmConfig fromEnv() {
        return new LlmConfig(
            envOrProperty("OPENAI_API_KEY"),
            envOrProperty("OPENAI_BASE_URL"),
            envOrProperty("OPENAI_MODEL"),
            parseDoubleOrNull(envOrProperty("LLM_TEMPERATURE")),
            parseIntOrNull(envOrProperty("LLM_MAX_RETRIES"))
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
            secrets.getOrDefault("base_url", envOrProperty("OPENAI_BASE_URL")),
            secrets.getOrDefault("model", envOrProperty("OPENAI_MODEL")),
            parseDoubleOrNull(secrets.get("temperature")),
            parseIntOrNull(secrets.get("max_retries"))
        );
    }

    private static String envOrProperty(String key) {
        var val = System.getenv(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        return System.getProperty("vault." + key);
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
