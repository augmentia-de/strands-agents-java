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

    private static Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
