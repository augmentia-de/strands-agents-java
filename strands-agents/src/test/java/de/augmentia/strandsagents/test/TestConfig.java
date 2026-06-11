package de.augmentia.strandsagents.test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TestConfig(
    RunConfig run,
    ModelConfig model,
    ToolsConfig tools,
    ConversationConfig conversation,
    SessionConfig session,
    ResilienceSection resilience,
    PluginsConfig plugins,
    List<HookConfig> hooks,
    @JsonProperty("structuredOutput") StructuredOutputSection structuredOutput,
    String systemPrompt,
    String testPrompt,
    AssertsConfig asserts,
    NextVariantConfig nextVariant
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RunConfig(int variant, String label, String timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelConfig(String type, String responseTemplate) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolsConfig(String preset, List<String> additional, List<String> include, List<String> exclude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConversationConfig(String type, Integer windowSize) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionConfig(String type, String directory) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResilienceSection(
        boolean enabled,
        RetryConfig retry,
        CircuitBreakerConfig circuitBreaker
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record RetryConfig(int maxAttempts, long backoffDelayMs, double backoffMultiplier) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record CircuitBreakerConfig(double failureRateThreshold, int slidingWindowSeconds, int halfOpenDelaySeconds) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PluginsConfig(
        GuardrailConfig guardrail,
        HitlConfig hitl
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record GuardrailConfig(boolean enabled, String blockAction, String fallbackMessage) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HitlConfig(boolean enabled, String authority, String mode) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HookConfig(String name, boolean enabled) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StructuredOutputSection(boolean enabled, String mode, String outputClass, String forcePrompt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssertsConfig(
        List<String> stopReason,
        boolean finalAnswerNotNull,
        long metricsDurationMsMin,
        int metricsToolCallsMin,
        String expectedOutputContains,
        String expectedOutputNotContains
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NextVariantConfig(String strategy, List<String> dimensions, int seed) {}
}
