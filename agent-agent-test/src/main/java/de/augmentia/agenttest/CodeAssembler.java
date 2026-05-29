package de.augmentia.agenttest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Collectors;

public class CodeAssembler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String assemble(WorkflowConfig config, StepSchemas schemas) {
        var steps = config.workflow();
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Workflow has no steps");
        }

        // 1. Tool-Liste: "mcp_localhost_8099_write", "mcp_localhost_8099_ls"
        var registryTools = config.tools().include().stream()
            .map(t -> "\"" + t + "\"")
            .collect(Collectors.joining(",\n            "));

        // 2. Steps-Execution + Results-Collection generieren
        var stepsExec = new StringBuilder();
        var resultsColl = new StringBuilder();
        var toolCallsSum = new StringBuilder();

        // SystemPrompt einmalig setzen (für alle Steps identisch)
        stepsExec.append(String.format(
            "agent.setSystemPrompt(\"%s\");\n\n",
            escapeJava(config.systemPrompt())
        ));

        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var key = String.valueOf(step.step());
            var schemaNode = schemas.stepSchemas().get(key);
            if (schemaNode == null) {
                throw new IllegalArgumentException("No schema found for step " + key);
            }
            var schemaJson = toSingleLineJson(schemaNode);

            // StructuredOutputConfig pro Step setzen
            stepsExec.append(String.format(
                "agent.setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema(\"%s\"));\n",
                escapeJava(schemaJson)
            ));

            // execute() – erster Step mit testPrompt, rest mit vorherigem Ergebnis
            if (i == 0) {
                stepsExec.append(String.format(
                    "var step%d = agent.execute(\"%s\");\n\n",
                    step.step(), escapeJava(config.testPrompt())
                ));
            } else {
                var prev = steps.get(i - 1);
                stepsExec.append(String.format(
                    "var step%d = agent.execute(\"Next: \" + step%d.finalAnswer());\n\n",
                    step.step(), prev.step()
                ));
            }

            // Ergebnis-Sammlung
            resultsColl.append(String.format(
                "out.put(\"step%d\", step%d.finalAnswer());\n",
                step.step(), step.step()
            ));

            // Tool-Calls zählen
            if (i > 0) toolCallsSum.append(" + ");
            toolCallsSum.append(String.format(
                "(step%d.metrics() != null ? step%d.metrics().toolCallsCount() : 0)",
                step.step(), step.step()
            ));
        }

        // StopReason vom letzten Step
        int lastStep = steps.get(steps.size() - 1).step();
        resultsColl.append(String.format(
            "out.put(\"stopReason\", step%d.stopReason().name());\n", lastStep
        ));
        resultsColl.append(String.format(
            "out.put(\"toolCalls\", %s);\n", toolCallsSum
        ));

        // 3. Template füllen
        return JavaCodeTemplate.CODE
            .replace("${REGISTRY_TOOLS}", registryTools)
            .replace("${STEPS_EXECUTION}", stepsExec.toString().strip())
            .replace("${RESULTS_COLLECTION}", resultsColl.toString().strip());
    }

    static String escapeJava(String s) {
        if (s == null) return "";
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    static String toSingleLineJson(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize schema: " + e.getMessage());
        }
    }
}
