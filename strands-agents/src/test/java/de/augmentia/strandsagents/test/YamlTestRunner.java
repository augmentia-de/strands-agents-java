package de.augmentia.strandsagents.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.MockChatModel;
import de.augmentia.strandsagents.core.DefaultToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.interceptor.guardrails.BlockAction;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.tools.builtin.CalculatorTool;
import de.augmentia.strandsagents.tools.builtin.TimeTool;
import de.augmentia.strandsagents.tools.builtin.WebSearchTool;
import de.augmentia.strandsagents.model.agent.StopReason;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class YamlTestRunner {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record TestResult(
        String label,
        boolean passed,
        String finalAnswer,
        StopReason stopReason,
        long durationMs,
        int toolCalls,
        List<String> errors
    ) {}

    public static TestConfig loadConfig(Path yamlPath) {
        try {
            return YAML.readValue(yamlPath.toFile(), TestConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test config: " + yamlPath, e);
        }
    }

    public static TestResult runConfig(Path yamlPath) {
        var config = loadConfig(yamlPath);
        return runConfig(config);
    }

    public static TestResult runConfig(TestConfig config) {
        var errors = new ArrayList<String>();

        try {
            var model = createModel(config);
            var toolRegistry = createToolRegistry(config);
            var conversationManager = createConversationManager(config);
            var plugins = createPlugins(config);
            var resilienceConfig = createResilienceConfig(config);

            var agent = new Agent(model, toolRegistry, new DefaultToolExecutor(),
                conversationManager, null, null, resilienceConfig, plugins);
            if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
                agent.setSystemPrompt(config.systemPrompt());
            }

            var so = config.structuredOutput();
            if (so != null && so.enabled() && so.outputClass() != null) {
                try {
                    var clazz = Class.forName(so.outputClass());
                    agent.setStructuredOutputConfig(StructuredOutputConfig.staticModel(clazz));
                } catch (ClassNotFoundException e) {
                    errors.add("Structured output class not found: " + so.outputClass());
                }
            }

            var start = System.currentTimeMillis();
            var result = agent.execute(config.testPrompt());
            var durationMs = System.currentTimeMillis() - start;

            var asserts = config.asserts();
            var passed = true;

            if (asserts != null) {
                if (asserts.stopReason() != null && !asserts.stopReason().isEmpty()) {
                    if (!asserts.stopReason().contains(result.stopReason().name())) {
                        errors.add("Expected stopReason in " + asserts.stopReason()
                            + " but got " + result.stopReason());
                        passed = false;
                    }
                }
                if (asserts.finalAnswerNotNull() && result.finalAnswer() == null) {
                    errors.add("Expected finalAnswer not null");
                    passed = false;
                }
                if (asserts.metricsDurationMsMin() > 0 && durationMs < asserts.metricsDurationMsMin()) {
                    errors.add("Duration " + durationMs + "ms < min " + asserts.metricsDurationMsMin() + "ms");
                    passed = false;
                }
                if (asserts.metricsToolCallsMin() > 0) {
                    var toolCalls = result.metrics() != null ? result.metrics().toolCallsCount() : 0;
                    if (toolCalls < asserts.metricsToolCallsMin()) {
                        errors.add("Tool calls " + toolCalls + " < min " + asserts.metricsToolCallsMin());
                        passed = false;
                    }
                }
                if (asserts.expectedOutputContains() != null
                    && !result.finalAnswer().contains(asserts.expectedOutputContains())) {
                    errors.add("Expected output to contain '" + asserts.expectedOutputContains()
                        + "' but got '" + result.finalAnswer() + "'");
                    passed = false;
                }
                if (asserts.expectedOutputNotContains() != null
                    && result.finalAnswer().contains(asserts.expectedOutputNotContains())) {
                    errors.add("Expected output NOT to contain '" + asserts.expectedOutputNotContains() + "'");
                    passed = false;
                }
            }

            var toolCalls = result.metrics() != null ? result.metrics().toolCallsCount() : 0;
            return new TestResult(config.run().label(), passed,
                result.finalAnswer(), result.stopReason(), durationMs, toolCalls, errors);

        } catch (Exception e) {
            errors.add("Execution error: " + e.getMessage());
            return new TestResult(config.run().label(), false, null, null, 0, 0, errors);
        }
    }

    private static ChatModel createModel(TestConfig config) {
        var mc = config.model();
        if ("mock".equalsIgnoreCase(mc.type())) {
            var template = mc.responseTemplate() != null ? mc.responseTemplate() : "Mock response: %s";
            return new MockChatModel(template);
        }
        throw new IllegalArgumentException("Unsupported model type: " + mc.type());
    }

    private static ToolRegistry createToolRegistry(TestConfig config) {
        var registry = new ToolRegistry();
        var tc = config.tools();
        if (tc == null) return registry;

        switch (tc.preset() != null ? tc.preset() : "empty") {
            case "minimal" -> {
                registry.register(new CalculatorTool());
                registry.register(new TimeTool());
            }
            case "standard" -> {
                registry.register(new CalculatorTool());
                registry.register(new TimeTool());
                registry.register(new WebSearchTool());
            }
        }

        if (tc.additional() != null) {
            for (var className : tc.additional()) {
                try {
                    var clazz = Class.forName(className);
                    var instance = clazz.getDeclaredConstructor().newInstance();
                    registry.register(instance);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to load tool: " + className, e);
                }
            }
        }

        if (tc.include() != null && !tc.include().isEmpty()) {
            var filtered = new ToolRegistry();
            for (var name : tc.include()) {
                try {
                    var tm = registry.get(name);
                    filtered.register(name, tm.spec(), tm);
                } catch (IllegalArgumentException ignored) {}
            }
            return filtered;
        }
        if (tc.exclude() != null && !tc.exclude().isEmpty()) {
            registry.removeAll(tc.exclude());
        }
        return registry;
    }

    private static ConversationManager createConversationManager(TestConfig config) {
        var cc = config.conversation();
        if (cc == null || cc.type() == null) return null;
        if ("sliding".equalsIgnoreCase(cc.type())) {
            return new SlidingWindowConversationManager(
                cc.windowSize() != null ? cc.windowSize() : 10);
        }
        return null;
    }

    private static ResilienceConfig createResilienceConfig(TestConfig config) {
        var rc = config.resilience();
        if (rc == null || !rc.enabled()) return ResilienceConfig.DEFAULT;
        var retry = rc.retry() != null
            ? new RetryConfig(rc.retry().maxAttempts(), rc.retry().backoffDelayMs(), rc.retry().backoffMultiplier())
            : RetryConfig.DEFAULT;
        var cb = rc.circuitBreaker() != null
            ? new CircuitBreakerConfig(
                (float) rc.circuitBreaker().failureRateThreshold(),
                rc.circuitBreaker().slidingWindowSeconds(),
                rc.circuitBreaker().halfOpenDelaySeconds())
            : CircuitBreakerConfig.DEFAULT;
        return new ResilienceConfig(retry, cb);
    }

    private static List<Plugin> createPlugins(TestConfig config) {
        var plugins = new ArrayList<Plugin>();
        var pc = config.plugins();
        if (pc == null) return plugins;

        if (pc.guardrail() != null && pc.guardrail().enabled()) {
            var blockAction = switch (pc.guardrail().blockAction()) {
                case "THROW" -> BlockAction.THROW;
                case "FALLBACK" -> BlockAction.FALLBACK;
                case "ESCALATE" -> BlockAction.ESCALATE;
                default -> BlockAction.FALLBACK;
            };
            plugins.add(new GuardrailPlugin(List.of(), List.of(), blockAction,
                pc.guardrail().fallbackMessage() != null ? pc.guardrail().fallbackMessage() : "Blocked"));
        }
        return plugins;
    }
}
