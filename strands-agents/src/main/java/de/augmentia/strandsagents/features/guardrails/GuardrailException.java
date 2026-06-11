package de.augmentia.strandsagents.features.guardrails;

public class GuardrailException extends RuntimeException {

    private final String guardrailReason;

    public GuardrailException(String guardrailReason) {
        super("Guardrail block: " + guardrailReason);
        this.guardrailReason = guardrailReason;
    }

    public String guardrailReason() {
        return guardrailReason;
    }
}
