package de.augmentia.strandsagents.core;

public interface HITLProvider {
    ApprovalResult requestApproval(String action, String context);
}
