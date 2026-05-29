package de.augmentia.agenttest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WorkflowConfig(
    String name,
    String description,
    String systemPrompt,
    String testPrompt,
    List<WorkflowStep> workflow,
    ToolConfig tools,
    AssertsConfig asserts
) {
    public record WorkflowStep(int step, String action, String tool) {}
    public record ToolConfig(List<String> include, List<String> exclude) {}
    public record AssertsConfig(Boolean finalAnswerNotNull, String expectedOutputContains) {}
}
