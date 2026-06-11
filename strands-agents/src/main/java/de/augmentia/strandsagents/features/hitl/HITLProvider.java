package de.augmentia.strandsagents.features.hitl;

import de.augmentia.strandsagents.features.guardrails.ApprovalResult;

public interface HITLProvider {
    ApprovalResult requestApproval(String action, String context);
}
