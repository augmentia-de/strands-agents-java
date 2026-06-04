package de.augmentia.strandsagents.core.prompt;

public final class PromptRegistry {

    private static volatile PromptManager instance;

    private PromptRegistry() {}

    public static PromptManager instance() {
        if (instance == null) {
            synchronized (PromptRegistry.class) {
                if (instance == null) {
                    instance = new YamlPromptManager("prompts.yaml");
                }
            }
        }
        return instance;
    }

    public static void configure(PromptManager pm) {
        instance = pm;
    }

    public static String get(String key, Object... args) {
        return instance().get(key, args);
    }

    public static String getOrDefault(String key, String fallback, Object... args) {
        return instance().getOrDefault(key, fallback, args);
    }
}
