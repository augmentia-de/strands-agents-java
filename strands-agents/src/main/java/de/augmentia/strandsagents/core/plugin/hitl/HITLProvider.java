package de.augmentia.strandsagents.core.plugin.hitl;

import de.augmentia.strandsagents.core.plugin.guardrail.ApprovalResult;

public interface HITLProvider {
    ApprovalResult requestApproval(String action, String context);
}
