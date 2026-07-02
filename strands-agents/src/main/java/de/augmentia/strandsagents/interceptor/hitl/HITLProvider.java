package de.augmentia.strandsagents.interceptor.hitl;

import de.augmentia.strandsagents.interceptor.guardrails.ApprovalResult;

public interface HITLProvider {
    ApprovalResult requestApproval(String action, String context);
}
