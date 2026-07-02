package de.augmentia.strandsagents.core.planning;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;

import java.util.List;

public interface Planner {

    Plan createPlan(String goal, List<String> availableToolNames);

    StepResult executeStep(Plan plan, int stepIndex, ToolExecutor executor, ToolRegistry registry);

    Plan revise(Plan plan, StepResult failure, String feedback);

    boolean isComplete(Plan plan, String finalOutput);

    int maxRevisions();
}
