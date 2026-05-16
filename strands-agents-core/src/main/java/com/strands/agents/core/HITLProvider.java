package com.strands.agents.core;

public interface HITLProvider {
    ApprovalResult requestApproval(String action, String context);
}
