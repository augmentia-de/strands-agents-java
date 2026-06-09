package de.augmentia.strandsagents.core.config;

import static de.augmentia.strandsagents.core.config.ConfigReader.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record TieredModelConfig(
    ChatModelConfig simple,
    ChatModelConfig advanced,
    ModelTier defaultTier
) {

    private static final Logger log = LoggerFactory.getLogger(TieredModelConfig.class);

    public static TieredModelConfig fromEnv() {
        var openaiApiKey = get("OPENAI_API_KEY", null);
        log.info("TieredModelConfig.fromEnv: OPENAI_API_KEY={}", ConfigReader.mask(openaiApiKey));
        // Global fallback: LLM_* and OPENAI_* env vars
        var globalFallback = new ChatModelConfig(
            ModelProviderType.OPENAI,
            get("OPENAI_API_KEY", null),
            get("OPENAI_BASE_URL", null),
            get("OPENAI_MODEL", null),
            parseDouble(get("LLM_TEMPERATURE", null)),
            parseInt(get("LLM_MAX_RETRIES", null)),
            null
        );

        var simple = ChatModelConfig.fromEnvWithFallback("SIMPLE_", globalFallback);
        var advanced = ChatModelConfig.fromEnvWithFallback("ADVANCED_", null);

        log.info("TieredModelConfig: simple apiKey={} model={} | advanced apiKey={} model={}",
            ConfigReader.mask(simple.apiKey()), simple.modelName(),
            ConfigReader.mask(advanced != null ? advanced.apiKey() : null),
            advanced != null ? advanced.modelName() : null);

        // Fallback: if ADVANCED has no config at all, derive from simple but with a different model name
        if (advanced == null || !hasAny("ADVANCED_")) {
            advanced = new ChatModelConfig(
                simple.provider(),
                simple.apiKey(),
                simple.baseUrl(),
                get("ADVANCED_MODEL", get("OPENAI_MODEL", "gpt-4o")),
                simple.temperature(),
                simple.maxRetries(),
                simple.ollamaBaseUrl()
            );
            log.info("TieredModelConfig: advanced derived from simple, model={}", advanced.modelName());
        }

        var defaultTier = ModelTier.fromString(get("LLM_DEFAULT_TIER", "simple"));

        log.info("TieredModelConfig: final simple.apiKey={} advanced.apiKey={}",
            ConfigReader.mask(simple.apiKey()), ConfigReader.mask(advanced.apiKey()));
        return new TieredModelConfig(simple, advanced, defaultTier);
    }

    public ChatModelConfig forTier(ModelTier tier) {
        return switch (tier) {
            case SIMPLE -> simple;
            case ADVANCED -> advanced;
            case ROUTING -> simple; // routing uses simple for initial analysis
        };
    }
}
