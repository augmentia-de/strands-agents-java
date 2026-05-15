package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentResult;
import java.util.concurrent.CompletableFuture;

public interface Agent {
    AgentResult execute(String prompt);

    default CompletableFuture<AgentResult> executeAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> execute(prompt));
    }
}
