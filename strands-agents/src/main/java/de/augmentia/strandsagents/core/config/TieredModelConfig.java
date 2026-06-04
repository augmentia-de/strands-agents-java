package de.augmentia.strandsagents.core.config;

public record TieredModelConfig(
    ChatModelConfig simple,
    ChatModelConfig advanced,
    ModelTier defaultTier
) {

    public static TieredModelConfig fromEnv() {
        // Global fallback: LLM_* and OPENAI_* env vars
        var globalFallback = new ChatModelConfig(
            ModelProviderType.OPENAI,
            envOrProperty("OPENAI_API_KEY", null),
            envOrProperty("OPENAI_BASE_URL", null),
            envOrProperty("OPENAI_MODEL", null),
            parseDoubleOrNull(envOrProperty("LLM_TEMPERATURE", null)),
            parseIntOrNull(envOrProperty("LLM_MAX_RETRIES", null)),
            null
        );

        var simple = ChatModelConfig.fromEnvWithFallback("SIMPLE_", globalFallback);
        var advanced = ChatModelConfig.fromEnvWithFallback("ADVANCED_", null);

        // Fallback: if ADVANCED has no config at all, derive from simple but with a different model name
        if (!hasAny("ADVANCED_")) {
            advanced = new ChatModelConfig(
                simple.provider(),
                simple.apiKey(),
                simple.baseUrl(),
                envOrProperty("ADVANCED_MODEL", envOrProperty("OPENAI_MODEL", "gpt-4o")),
                simple.temperature(),
                simple.maxRetries(),
                simple.ollamaBaseUrl()
            );
        }

        var defaultTier = ModelTier.fromString(envOrProperty("LLM_DEFAULT_TIER", "simple"));

        return new TieredModelConfig(simple, advanced, defaultTier);
    }

    public ChatModelConfig forTier(ModelTier tier) {
        return switch (tier) {
            case SIMPLE -> simple;
            case ADVANCED -> advanced;
            case ROUTING -> simple; // routing uses simple for initial analysis
        };
    }

    private static boolean hasAny(String prefix) {
        return System.getenv(prefix + "PROVIDER") != null
            || System.getenv(prefix + "API_KEY") != null
            || System.getenv(prefix + "BASE_URL") != null
            || System.getenv(prefix + "MODEL") != null;
    }

    private static String envOrProperty(String key, String fallback) {
        var val = System.getenv(key);
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
