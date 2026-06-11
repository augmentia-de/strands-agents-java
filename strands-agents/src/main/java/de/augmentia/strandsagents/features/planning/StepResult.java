package de.augmentia.strandsagents.features.planning;

import java.util.Map;

public record StepResult(
    boolean success,
    String output,
    String error,
    Map<String, Object> artifacts
) {

    public StepResult(boolean success, String output) {
        this(success, output, null, Map.of());
    }

    public StepResult(boolean success, String output, String error) {
        this(success, output, error, Map.of());
    }

    public static StepResult ok(String output) {
        return new StepResult(true, output, null, Map.of());
    }

    public static StepResult ok(String output, Map<String, Object> artifacts) {
        return new StepResult(true, output, null, artifacts);
    }

    public static StepResult fail(String error) {
        return new StepResult(false, null, error, Map.of());
    }
}
