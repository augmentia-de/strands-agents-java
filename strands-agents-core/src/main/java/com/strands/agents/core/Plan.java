package com.strands.agents.core;

import java.util.List;
import java.util.Map;

public record Plan(
    String goal,
    List<Step> steps,
    int currentStep,
    Map<String, Object> sharedContext
) {

    public Plan withStep(int index) {
        return new Plan(goal, steps, index, sharedContext);
    }

    public Plan advanceStep() {
        return new Plan(goal, steps, currentStep + 1, sharedContext);
    }

    public Plan withSharedContext(Map<String, Object> context) {
        return new Plan(goal, steps, currentStep, context);
    }

    public boolean isComplete() {
        return currentStep >= steps.size();
    }

    public Step current() {
        if (isComplete()) return null;
        return steps.get(currentStep);
    }
}
