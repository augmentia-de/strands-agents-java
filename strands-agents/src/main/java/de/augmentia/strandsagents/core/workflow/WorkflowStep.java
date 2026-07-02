package de.augmentia.strandsagents.core.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowStep(
    String id,
    String role,
    String type,
    String description,
    List<String> next,
    Map<String, String> inputMapping,
    Map<String, String> outputMapping
) {}
