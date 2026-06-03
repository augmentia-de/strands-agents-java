package de.augmentia.strandsagents.core.agent.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.*;
import java.util.stream.Collectors;

public class CoTPlanner implements Planner {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_REVISIONS = 3;

    private final ChatModel model;
    private final int maxRevisions;

    public CoTPlanner(ChatModel model) {
        this(model, DEFAULT_MAX_REVISIONS);
    }

    public CoTPlanner(ChatModel model, int maxRevisions) {
        this.model = model;
        this.maxRevisions = maxRevisions;
    }

    @Override
    public int maxRevisions() {
        return maxRevisions;
    }

    @Override
    public Plan createPlan(String goal, List<String> availableToolNames) {
        var systemPrompt = """
            You are a planning assistant. Break down complex goals into individual, executable steps.
            
            Each step must be assigned to exactly ONE available tool.
            
            Available tools: %s
            
            Respond ONLY with a JSON array. No explanations, no markdown.
            Each object in the array has the following fields:
            - "id": unique identifier (e.g. "step-1")
            - "description": description of the step
            - "toolName": name of the tool to use (from the list of available tools)
            - "argumentsTemplate": placeholder for tool arguments, e.g. "${value}" 
            - "dependsOn": array of IDs this step depends on (empty array if none)
            - "optional": true/false
            
            If no tool is needed for a step, set "toolName" to "none".
            """.formatted(formatToolNames(availableToolNames));

        var userPrompt = "Create a plan for the following goal:\n%s".formatted(goal);

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
        var systemPrompt = """
            A previous plan has failed. Create a REVISED plan.
            
            Respond ONLY with a JSON array. Each object has:
            - "id": unique identifier
            - "description": description of the step
            - "toolName": tool name or "none"
            - "argumentsTemplate": arguments
            - "dependsOn": array of dependencies
            - "optional": true/false
            """;

        var userPrompt = """
            Original goal: %s
            
            Failed step: %s
            Error: %s
            
            Feedback: %s
            
            Previous context: %s
            
            Create a new, corrected plan.
            """.formatted(
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
        var prompt = """
            Check whether the following goal has been achieved.
            
            Goal: %s
            
            Result: %s
            
            Answer exclusively with "true" or "false".
            """.formatted(plan.goal(), finalOutput);

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

    private static String formatToolNames(List<String> names) {
        if (names == null || names.isEmpty()) return "none (only 'none' allowed)";
        return names.stream().collect(Collectors.joining(", "));
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
