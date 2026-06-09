package de.augmentia.strandsagents.core.config;

import static de.augmentia.strandsagents.core.config.ConfigReader.*;

public enum ModelProviderType {
    OPENAI,
    OLLAMA,
    OPENAI_COMPATIBLE;

    public static ModelProviderType fromEnv(String prefix) {
        var val = get(prefix + "PROVIDER");
        if (val == null || val.isBlank()) return OPENAI;
        return fromString(val);
    }

    public static ModelProviderType fromString(String s) {
        if (s == null || s.isBlank()) return OPENAI;
        return switch (s.trim().toLowerCase()) {
            case "ollama" -> OLLAMA;
            case "openai-compatible" -> OPENAI_COMPATIBLE;
            default -> OPENAI;
        };
    }
}
