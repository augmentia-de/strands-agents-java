package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.core.model.agent.AgentResult;
import java.util.concurrent.CompletableFuture;

public interface Agent {
    AgentResult execute(String prompt);

    default CompletableFuture<AgentResult> executeAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> execute(prompt));
    }
}
