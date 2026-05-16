package com.strands.agents.core;

public record LlmConfig(
    String apiKey,
    String baseUrl,
    String modelName,
    Double temperature,
    Integer maxRetries
) {

    public static LlmConfig fromEnv() {
        return new LlmConfig(
            System.getenv("OPENAI_API_KEY"),
            System.getenv("OPENAI_BASE_URL"),
            System.getenv("LLM_CHAT_MODEL"),
            parseDoubleOrNull(System.getenv("LLM_TEMPERATURE")),
            parseIntOrNull(System.getenv("LLM_MAX_RETRIES"))
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
            secrets.getOrDefault("base_url", System.getenv("OPENAI_BASE_URL")),
            secrets.getOrDefault("model", System.getenv("LLM_CHAT_MODEL")),
            parseDoubleOrNull(secrets.get("temperature")),
            parseIntOrNull(secrets.get("max_retries"))
        );
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
