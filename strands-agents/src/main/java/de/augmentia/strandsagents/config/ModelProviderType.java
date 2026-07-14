package de.augmentia.strandsagents.config;

import static de.augmentia.strandsagents.config.ConfigReader.*;

/** Supported LLM provider types. */
public enum ModelProviderType {
    OPENAI,
    OLLAMA,
    OPENAI_COMPATIBLE;

    /** Reads provider type from the environment variable <prefix>PROVIDER. */
    public static ModelProviderType fromEnv(String prefix) {
        var val = get(prefix + "PROVIDER");
        if (val == null || val.isBlank()) return OPENAI;
        return fromString(val);
    }

    /** Parses a string to a ModelProviderType, defaulting to OPENAI. */
    public static ModelProviderType fromString(String s) {
        if (s == null || s.isBlank()) return OPENAI;
        return switch (s.trim().toLowerCase()) {
            case "ollama" -> OLLAMA;
            case "openai-compatible" -> OPENAI_COMPATIBLE;
            default -> OPENAI;
        };
    }
}
