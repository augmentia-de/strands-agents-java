package de.augmentia.strandsagents.features.guardrails;

import java.time.Instant;

public record ApprovalResult(
    String action,
    boolean approved,
    String feedback,
    Instant timestamp
) {

    public static ApprovalResult approved(String action) {
        return new ApprovalResult(action, true, null, Instant.now());
    }

    public static ApprovalResult denied(String action, String feedback) {
        return new ApprovalResult(action, false, feedback, Instant.now());
    }
}
