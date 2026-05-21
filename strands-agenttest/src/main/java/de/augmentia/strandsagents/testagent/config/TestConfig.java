package de.augmentia.strandsagents.testagent.config;

import java.util.List;

public record TestConfig(
    RunConfig run,
    ModelConfig model,
    ToolConfig tools,
    ConversationConfig conversation,
    SessionConfig session,
    ResilienceBlock resilience,
    PluginBlock plugins,
    List<HookEntry> hooks,
    StructuredOutputBlock structuredOutput,
    String systemPrompt,
    String testPrompt,
    AssertConfig asserts,
    NextVariantConfig nextVariant
) {

    public record RunConfig(int variant, String label, String timestamp) {}
    public record ModelConfig(String type, String responseTemplate) {}
    public record ToolConfig(String preset, List<String> additional,
                             List<String> include, List<String> exclude) {}
    public record ConversationConfig(String type, int windowSize) {}
    public record SessionConfig(String type, String directory) {}

    public record ResilienceBlock(boolean enabled, RetryBlock retry,
                                  CircuitBreakerBlock circuitBreaker) {
        public record RetryBlock(int maxAttempts, long backoffDelayMs,
                                 double backoffMultiplier) {}
        public record CircuitBreakerBlock(float failureRateThreshold,
                                          long slidingWindowSeconds,
                                          long halfOpenDelaySeconds) {}
    }

    public record PluginBlock(PluginGuardrailBlock guardrail,
                              PluginHitlBlock hitl) {
        public record PluginGuardrailBlock(boolean enabled, String blockAction,
                                           String fallbackMessage) {}
        public record PluginHitlBlock(boolean enabled, String authority,
                                      String mode) {}
    }

    public record HookEntry(String name, boolean enabled) {}

    public record StructuredOutputBlock(boolean enabled, String mode,
                                        String outputClass,
                                        String forcePrompt) {}

    public record AssertConfig(List<String> stopReason,
                               boolean finalAnswerNotNull,
                               long metricsDurationMsMin,
                               int metricsToolCallsMin,
                               String expectedOutputContains,
                               String expectedOutputNotContains) {}

    public record NextVariantConfig(String strategy, List<String> dimensions,
                                    int seed) {}
}
