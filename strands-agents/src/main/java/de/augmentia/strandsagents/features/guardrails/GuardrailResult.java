package de.augmentia.strandsagents.features.guardrails;

public record GuardrailResult(
    boolean pass,
    String reason,
    String sanitized
) {

    public static GuardrailResult ok() {
        return new GuardrailResult(true, null, null);
    }

    public static GuardrailResult block(String reason) {
        return new GuardrailResult(false, reason, null);
    }

    public static GuardrailResult block(String reason, String sanitized) {
        return new GuardrailResult(false, reason, sanitized);
    }
}
