package de.augmentia.strandsagents.testagent.engine;

import de.augmentia.strandsagents.testagent.config.TestConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class VariantEngine {

    private VariantEngine() {}

    public static final Map<String, List<Object>> DIMENSIONS = new LinkedHashMap<>();
    static {
        DIMENSIONS.put("tools.preset", List.of("standard", "empty", "minimal"));
        DIMENSIONS.put("conversation.type", List.of("sliding", "none"));
        DIMENSIONS.put("session.type", List.of("file", "none"));
        DIMENSIONS.put("resilience.enabled", List.of(true, false));
        DIMENSIONS.put("plugins.guardrail.enabled", List.of(true, false));
        DIMENSIONS.put("plugins.hitl.enabled", List.of(true, false));
        DIMENSIONS.put("hooks", List.of("none", "logging"));
        DIMENSIONS.put("structuredOutput.enabled", List.of(true, false));
    }

    public static Optional<TestConfig> next(TestConfig current) {
        var combinations = buildCombinations(current.nextVariant());
        int idx = current.run().variant(); // 0-based index
        if (idx >= combinations.size()) {
            return Optional.empty();
        }
        var combo = combinations.get(idx);
        var next = apply(current, combo, idx + 1);
        return Optional.of(next);
    }

    public static int totalVariants(TestConfig config) {
        return buildCombinations(config.nextVariant()).size();
    }

    static List<Map<String, Object>> buildCombinations(
            TestConfig.NextVariantConfig cfg) {
        var activeDims = new ArrayList<Map.Entry<String, List<Object>>>();
        for (var dim : cfg.dimensions()) {
            var values = DIMENSIONS.get(dim);
            if (values != null) {
                activeDims.add(Map.entry(dim, values));
            }
        }
        return switch (cfg.strategy()) {
            case "linear" -> linearProduct(activeDims);
            case "exhaustive" -> cartesianProduct(activeDims);
            default -> linearProduct(activeDims);
        };
    }

    static List<Map<String, Object>> linearProduct(
            List<Map.Entry<String, List<Object>>> dims) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : dims) {
            for (var value : entry.getValue()) {
                var map = new LinkedHashMap<String, Object>();
                map.put(entry.getKey(), value);
                result.add(map);
            }
        }
        return result;
    }

    static List<Map<String, Object>> cartesianProduct(
            List<Map.Entry<String, List<Object>>> dims) {
        var result = new ArrayList<Map<String, Object>>();
        cartesianRecurse(dims, 0, new LinkedHashMap<>(), result);
        return result;
    }

    private static void cartesianRecurse(
            List<Map.Entry<String, List<Object>>> dims,
            int depth,
            LinkedHashMap<String, Object> current,
            List<Map<String, Object>> result) {
        if (depth >= dims.size()) {
            result.add(new LinkedHashMap<>(current));
            return;
        }
        var entry = dims.get(depth);
        for (var value : entry.getValue()) {
            current.put(entry.getKey(), value);
            cartesianRecurse(dims, depth + 1, current, result);
            current.remove(entry.getKey());
        }
    }

    static TestConfig apply(TestConfig base,
                            Map<String, Object> values,
                            int variant) {
        var run = new TestConfig.RunConfig(
            variant,
            buildLabel(values, variant),
            java.time.Instant.now().toString()
        );

        var tools = base.tools() != null ? applyTools(base.tools(), values) : base.tools();
        var conv = base.conversation() != null ? applyConversation(base.conversation(), values) : base.conversation();
        var session = base.session() != null ? applySession(base.session(), values) : base.session();
        var resilience = base.resilience() != null ? applyResilience(base.resilience(), values) : base.resilience();
        var plugins = base.plugins() != null ? applyPlugins(base.plugins(), values) : base.plugins();
        var hooks = applyHooks(base.hooks(), values);
        var structured = applyStructured(base.structuredOutput(), values);

        return new TestConfig(run, base.model(), tools, conv, session,
            resilience, plugins, hooks, structured,
            base.systemPrompt(), base.testPrompt(),
            base.asserts(), base.nextVariant());
    }

    private static String buildLabel(Map<String, Object> values, int variant) {
        var sb = new StringBuilder("variant_").append(variant).append("__");
        for (var e : values.entrySet()) {
            var key = e.getKey().replaceAll("[^a-zA-Z0-9]", "_");
            sb.append(key).append("=").append(e.getValue()).append("_");
        }
        return sb.toString().replaceAll("_$", "");
    }

    private static String strVal(Map<String, Object> values, String key) {
        var v = values.get(key);
        return v != null ? v.toString() : null;
    }

    private static boolean boolVal(Map<String, Object> values, String key) {
        var v = values.get(key);
        return v instanceof Boolean b && b;
    }

    static TestConfig.ToolConfig applyTools(TestConfig.ToolConfig base,
                                             Map<String, Object> values) {
        var preset = strVal(values, "tools.preset");
        if (preset == null) return base;
        return new TestConfig.ToolConfig(preset, base.additional(),
            base.include(), base.exclude());
    }

    static TestConfig.ConversationConfig applyConversation(
            TestConfig.ConversationConfig base,
            Map<String, Object> values) {
        var type = strVal(values, "conversation.type");
        if (type == null) return base;
        if ("none".equals(type)) return null;
        return new TestConfig.ConversationConfig(type, base.windowSize());
    }

    static TestConfig.SessionConfig applySession(
            TestConfig.SessionConfig base,
            Map<String, Object> values) {
        var type = strVal(values, "session.type");
        if (type == null) return base;
        if ("none".equals(type)) return null;
        return new TestConfig.SessionConfig(type, base.directory());
    }

    static TestConfig.ResilienceBlock applyResilience(
            TestConfig.ResilienceBlock base,
            Map<String, Object> values) {
        var enabled = values.containsKey("resilience.enabled")
            ? boolVal(values, "resilience.enabled") : base.enabled();
        return new TestConfig.ResilienceBlock(enabled, base.retry(),
            base.circuitBreaker());
    }

    static TestConfig.PluginBlock applyPlugins(
            TestConfig.PluginBlock base,
            Map<String, Object> values) {
        var guardrail = base.guardrail();
        if (values.containsKey("plugins.guardrail.enabled")) {
            guardrail = new TestConfig.PluginBlock.PluginGuardrailBlock(
                boolVal(values, "plugins.guardrail.enabled"),
                guardrail.blockAction(), guardrail.fallbackMessage());
        }
        var hitl = base.hitl();
        if (values.containsKey("plugins.hitl.enabled")) {
            hitl = new TestConfig.PluginBlock.PluginHitlBlock(
                boolVal(values, "plugins.hitl.enabled"),
                hitl.authority(), hitl.mode());
        }
        return new TestConfig.PluginBlock(guardrail, hitl);
    }

    static List<TestConfig.HookEntry> applyHooks(
            List<TestConfig.HookEntry> base,
            Map<String, Object> values) {
        var hookVal = strVal(values, "hooks");
        if (hookVal == null) return base;
        if ("none".equals(hookVal)) return List.of();
        return List.of(new TestConfig.HookEntry(hookVal, true));
    }

    static TestConfig.StructuredOutputBlock applyStructured(
            TestConfig.StructuredOutputBlock base,
            Map<String, Object> values) {
        if (!values.containsKey("structuredOutput.enabled")) return base;
        return new TestConfig.StructuredOutputBlock(
            boolVal(values, "structuredOutput.enabled"),
            base.mode(), base.outputClass(), base.forcePrompt());
    }
}
