package de.augmentia.strandsagents.core.config;

public enum ModelTier {
    SIMPLE,
    ADVANCED,
    ROUTING;

    public static ModelTier fromString(String s) {
        if (s == null || s.isBlank()) return SIMPLE;
        return switch (s.trim().toLowerCase()) {
            case "advanced" -> ADVANCED;
            case "routing" -> ROUTING;
            default -> SIMPLE;
        };
    }
}
