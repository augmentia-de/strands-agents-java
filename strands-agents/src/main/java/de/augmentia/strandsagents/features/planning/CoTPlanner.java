package de.augmentia.strandsagents.features.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.*;
import java.util.stream.Collectors;

public class CoTPlanner implements Planner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_REVISIONS = 3;

    private final ChatModel model;
    private final int maxRevisions;
    private final ToolRegistry toolRegistry;

    public CoTPlanner(ChatModel model) {
        this(model, DEFAULT_MAX_REVISIONS, null);
    }

    public CoTPlanner(ChatModel model, int maxRevisions) {
        this(model, maxRevisions, null);
    }

    public CoTPlanner(ChatModel model, int maxRevisions, ToolRegistry toolRegistry) {
        this.model = model;
        this.maxRevisions = maxRevisions;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public int maxRevisions() {
        return maxRevisions;
    }

    @Override
    public Plan createPlan(String goal, List<String> availableToolNames) {
        var systemPrompt = PromptRegistry.get("cot_planner.create_plan.system", formatToolNames(availableToolNames));
        var userPrompt = PromptRegistry.get("cot_planner.create_plan.user", goal);

        var response = model.chat(ChatRequest.builder()
            .messages(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            ))
            .build());

        var text = response.aiMessage().text();
        var steps = parseSteps(text);

        if (steps.isEmpty()) {
            steps = List.of(new Step("step-1", "Execute: " + goal, "none", goal));
        }

        return new Plan(goal, steps, 0, new HashMap<>());
    }

    @Override
    public StepResult executeStep(Plan plan, int stepIndex, ToolExecutor executor, ToolRegistry registry) {
        var step = plan.steps().get(stepIndex);
        var context = plan.sharedContext();

        try {
            if ("none".equals(step.toolName())) {
                var resolved = resolveTemplate(step.argumentsTemplate(), context);
                return StepResult.ok(resolved, Map.of("result", resolved));
            }

            var toolMethod = registry.get(step.toolName());
            var resolvedArgs = resolveTemplate(step.argumentsTemplate(), context);
            var result = toolMethod.execute(resolvedArgs);

            return StepResult.ok(result, Map.of("result", result));
        } catch (Exception e) {
            return StepResult.fail("Step '%s' failed: %s".formatted(step.id(), e.getMessage()));
        }
    }

    @Override
    public Plan revise(Plan plan, StepResult failure, String feedback) {
        var systemPrompt = PromptRegistry.get("cot_planner.revise.system");
        var userPrompt = PromptRegistry.get("cot_planner.revise.user",
                plan.goal(),
                plan.steps().get(Math.min(plan.currentStep(), plan.steps().size() - 1)),
                failure.error(),
                feedback,
                plan.sharedContext()
            );

        var response = model.chat(ChatRequest.builder()
            .messages(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            ))
            .build());

        var steps = parseSteps(response.aiMessage().text());

        if (steps.isEmpty()) {
            steps = List.of(new Step("step-1", "Execute: " + plan.goal(), "none", plan.goal()));
        }

        return new Plan(plan.goal(), steps, 0, plan.sharedContext());
    }

    @Override
    public boolean isComplete(Plan plan, String finalOutput) {
        var prompt = PromptRegistry.get("cot_planner.is_complete.user", plan.goal(), finalOutput);

        var response = model.chat(ChatRequest.builder()
            .messages(List.of(
                UserMessage.from(prompt)
            ))
            .build());

        var text = response.aiMessage().text().strip().toLowerCase();
        return text.contains("true");
    }

    private List<Step> parseSteps(String jsonText) {
        try {
            var cleaned = jsonText.strip();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```(?:json)?", "").strip();
            }

            var rawList = MAPPER.readValue(cleaned, List.class);
            var steps = new ArrayList<Step>();

            for (var item : rawList) {
                if (item instanceof Map<?, ?> map) {
                    steps.add(new Step(
                        stringOr(map.get("id"), "step-" + (steps.size() + 1)),
                        stringOr(map.get("description"), ""),
                        stringOr(map.get("toolName"), "none"),
                        stringOr(map.get("argumentsTemplate"), ""),
                        stringList(map.get("dependsOn")),
                        boolOr(map.get("optional"), false)
                    ));
                }
            }
            return steps;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String formatToolNames(List<String> names) {
        if (names == null || names.isEmpty()) return "none (only 'none' allowed)";
        if (toolRegistry == null) return names.stream().collect(Collectors.joining(", "));
        var specs = toolRegistry.getSpecifications().stream()
            .collect(Collectors.toMap(ToolSpecification::name, s -> s, (a, b) -> a));
        return names.stream()
            .map(n -> {
                var spec = specs.get(n);
                if (spec == null) return "- " + n;
                return formatToolSpec(spec);
            })
            .collect(Collectors.joining("\n"));
    }

    private static String formatToolSpec(ToolSpecification spec) {
        var sb = new StringBuilder();
        sb.append("- ").append(spec.name());
        if (spec.description() != null) {
            sb.append(": ").append(spec.description());
        }
        var params = spec.parameters();
        if (params != null && params.properties() != null && !params.properties().isEmpty()) {
            sb.append("  Parameters:");
            for (var prop : params.properties().entrySet()) {
                sb.append("\n    * ").append(prop.getKey());
            }
            if (params.required() != null && !params.required().isEmpty()) {
                sb.append("\n    Required: ").append(String.join(", ", params.required()));
            }
        }
        return sb.toString();
    }

    private static String resolveTemplate(String template, Map<String, Object> context) {
        if (template == null) return "";
        var result = template;
        for (var entry : context.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private static String stringOr(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private static boolean boolOr(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return fallback;
    }
}
