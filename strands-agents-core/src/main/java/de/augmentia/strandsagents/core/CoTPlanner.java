package de.augmentia.strandsagents.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            Du bist ein Planungs-Assistent. Zerlege komplexe Ziele in einzelne, ausführbare Schritte.
            
            Jeder Schritt muss genau EINEM verfügbaren Tool zugeordnet sein.
            
            Verfügbare Tools: %s
            
            Antworte NUR mit einem JSON-Array. Keine Erklärungen, kein Markdown.
            Jedes Objekt im Array hat folgende Felder:
            - "id": eindeutige Kennung (z.B. "step-1")
            - "description": Beschreibung des Schritts
            - "toolName": Name des zu verwendenden Tools (aus der Liste der verfügbaren Tools)
            - "argumentsTemplate": Platzhalter für Tool-Argumente, z.B. "${wert}" 
            - "dependsOn": Array von IDs, von denen dieser Schritt abhängt (leeres Array, wenn keine)
            - "optional": true/false
            
            Falls kein Tool für einen Schritt benötigt wird, setze "toolName" auf "none".
            """.formatted(formatToolNames(availableToolNames));

        var userPrompt = "Erstelle einen Plan für das folgende Ziel:\n%s".formatted(goal);

        var response = model.chat(ChatRequest.builder()
            .messages(List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
            ))
            .build());

        var text = response.aiMessage().text();
        var steps = parseSteps(text);

        if (steps.isEmpty()) {
            steps = List.of(new Step("step-1", "Führe aus: " + goal, "none", goal));
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
            return StepResult.fail("Schritt '%s' fehlgeschlagen: %s".formatted(step.id(), e.getMessage()));
        }
    }

    @Override
    public Plan revise(Plan plan, StepResult failure, String feedback) {
        var systemPrompt = """
            Ein vorheriger Plan ist fehlgeschlagen. Erstelle einen REVIDIERTEN Plan.
            
            Antworte NUR mit einem JSON-Array. Jedes Objekt hat:
            - "id": eindeutige Kennung
            - "description": Beschreibung des Schritts
            - "toolName": Tool-Name oder "none"
            - "argumentsTemplate": Argumente
            - "dependsOn": Array von Abhängigkeiten
            - "optional": true/false
            """;

        var userPrompt = """
            Ursprüngliches Ziel: %s
            
            Fehlgeschlagener Schritt: %s
            Fehler: %s
            
            Feedback: %s
            
            Bisheriger Kontext: %s
            
            Erstelle einen neuen, korrigierten Plan.
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
            steps = List.of(new Step("step-1", "Führe aus: " + plan.goal(), "none", plan.goal()));
        }

        return new Plan(plan.goal(), steps, 0, plan.sharedContext());
    }

    @Override
    public boolean isComplete(Plan plan, String finalOutput) {
        var prompt = """
            Prüfe, ob das folgende Ziel erreicht wurde.
            
            Ziel: %s
            
            Ergebnis: %s
            
            Antworte ausschließlich mit "true" oder "false".
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
        if (names == null || names.isEmpty()) return "keine (nur 'none' erlaubt)";
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
