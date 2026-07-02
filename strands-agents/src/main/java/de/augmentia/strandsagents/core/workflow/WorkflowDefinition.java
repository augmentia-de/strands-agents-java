package de.augmentia.strandsagents.core.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(
    String id,
    String name,
    String description,
    String startStep,
    List<WorkflowStep> steps,
    Map<String, Object> globalContext
) {}
