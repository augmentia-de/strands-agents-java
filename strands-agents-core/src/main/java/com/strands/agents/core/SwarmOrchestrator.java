package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.agent.ExecutionMetrics;
import com.strands.agents.core.model.agent.StopReason;
import java.util.List;
import java.util.Map;

public class SwarmOrchestrator implements Agent {

    private final List<Route> routes;
    private final Agent defaultAgent;
    private final String sessionId;

    public SwarmOrchestrator(List<Route> routes, Agent defaultAgent) {
        this.routes = List.copyOf(routes);
        this.defaultAgent = defaultAgent;
        this.sessionId = java.util.UUID.randomUUID().toString();
    }

    public SwarmOrchestrator(Map<String, Agent> topicRoutes, Agent defaultAgent) {
        this(topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent);
    }

    @Override
    public AgentResult execute(String prompt) {
        var lower = prompt.toLowerCase();
        for (var route : routes) {
            if (lower.contains(route.topic().toLowerCase())) {
                var result = route.agent().execute(prompt);
                return new AgentResult(
                    sessionId,
                    "[Orchestrator → " + route.topic() + "]: " + result.finalAnswer(),
                    result.generatedMessages(),
                    result.metrics(),
                    result.stopReason()
                );
            }
        }
        var result = defaultAgent.execute(prompt);
        return new AgentResult(
            sessionId,
            "[Orchestrator → Default]: " + result.finalAnswer(),
            result.generatedMessages(),
            result.metrics(),
            result.stopReason()
        );
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public record Route(String topic, Agent agent) {}
}
