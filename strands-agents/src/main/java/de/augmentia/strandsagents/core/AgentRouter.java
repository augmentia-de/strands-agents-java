package de.augmentia.strandsagents.core;

import java.util.List;

public interface AgentRouter {
    RoutingDecision route(String prompt, List<String> availableModels);

    record RoutingDecision(String selectedModel, double confidence, String originalPrompt) {}
}