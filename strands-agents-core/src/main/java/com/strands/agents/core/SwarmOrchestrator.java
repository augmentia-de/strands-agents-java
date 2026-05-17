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
    private final LlmRouter router;

    public SwarmOrchestrator(List<Route> routes, Agent defaultAgent) {
        this(null, routes, defaultAgent);
    }

    public SwarmOrchestrator(Map<String, Agent> topicRoutes, Agent defaultAgent) {
        this(null, topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent);
    }

    public SwarmOrchestrator(LlmRouter router, List<Route> routes, Agent defaultAgent) {
        this.router = router;
        this.routes = List.copyOf(routes);
        this.defaultAgent = defaultAgent;
        this.sessionId = java.util.UUID.randomUUID().toString();
    }

    public SwarmOrchestrator(LlmRouter router, Map<String, Agent> topicRoutes, Agent defaultAgent) {
        this(router, topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent);
    }

    @Override
    public AgentResult execute(String prompt) {
        Route matchedRoute = null;

        if (router != null) {
            var topics = routes.stream().map(Route::topic).toList();
            var result = router.classify(prompt, topics);

            if (result.confidence() >= router.getConfidenceThreshold()) {
                matchedRoute = findRoute(result.topic());
            }
        }

        if (matchedRoute == null) {
            var lower = prompt.toLowerCase();
            for (var route : routes) {
                if (lower.contains(route.topic().toLowerCase())) {
                    matchedRoute = route;
                    break;
                }
            }
        }

        if (matchedRoute != null) {
            var result = matchedRoute.agent().execute(prompt);
            return new AgentResult(
                sessionId,
                "[Orchestrator → " + matchedRoute.topic() + "]: " + result.finalAnswer(),
                result.generatedMessages(),
                result.metrics(),
                result.stopReason(),
                result.structuredOutput()
            );
        }

        var result = defaultAgent.execute(prompt);
        return new AgentResult(
            sessionId,
            "[Orchestrator → Default]: " + result.finalAnswer(),
            result.generatedMessages(),
            result.metrics(),
            result.stopReason(),
            result.structuredOutput()
        );
    }

    private Route findRoute(String topic) {
        return routes.stream()
            .filter(r -> r.topic().equalsIgnoreCase(topic))
            .findFirst()
            .orElse(null);
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public LlmRouter getRouter() {
        return router;
    }

    public record Route(String topic, Agent agent) {}
}
