package de.augmentia.strandsagents.features.swarm;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.features.routing.LlmRouter;
import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.model.agent.AgentResult;
import de.augmentia.strandsagents.model.session.Session;

import java.util.List;
import java.util.Map;

public class SwarmOrchestrator {

    private final List<Route> routes;
    private final Agent defaultAgent;
    private final String sharedSessionId;
    private final LlmRouter router;
    private final SessionManager sessionManager;

    public SwarmOrchestrator(List<Route> routes, Agent defaultAgent) {
        this(null, routes, defaultAgent, null);
    }

    public SwarmOrchestrator(List<Route> routes, Agent defaultAgent, SessionManager sessionManager) {
        this(null, routes, defaultAgent, sessionManager);
    }

    public SwarmOrchestrator(Map<String, Agent> topicRoutes, Agent defaultAgent) {
        this(null, topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent, null);
    }

    public SwarmOrchestrator(Map<String, Agent> topicRoutes, Agent defaultAgent, SessionManager sessionManager) {
        this(null, topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent, sessionManager);
    }

    public SwarmOrchestrator(LlmRouter router, List<Route> routes, Agent defaultAgent) {
        this(router, routes, defaultAgent, null);
    }

    public SwarmOrchestrator(LlmRouter router, List<Route> routes, Agent defaultAgent, SessionManager sessionManager) {
        this.router = router;
        this.routes = List.copyOf(routes);
        this.defaultAgent = defaultAgent;
        this.sessionManager = sessionManager;
        if (sessionManager != null) {
            var session = sessionManager.createSession("swarm", Map.of());
            this.sharedSessionId = session.sessionId();
        } else {
            this.sharedSessionId = java.util.UUID.randomUUID().toString();
        }
    }

    public SwarmOrchestrator(LlmRouter router, Map<String, Agent> topicRoutes, Agent defaultAgent) {
        this(router, topicRoutes, defaultAgent, null);
    }

    public SwarmOrchestrator(LlmRouter router, Map<String, Agent> topicRoutes, Agent defaultAgent, SessionManager sessionManager) {
        this(router, topicRoutes.entrySet().stream()
            .map(e -> new Route(e.getKey(), e.getValue()))
            .toList(), defaultAgent, sessionManager);
    }

    //@Override
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
            var result = matchedRoute.agent().execute(sharedSessionId, prompt);
            return new AgentResult(
                sharedSessionId,
                "[Orchestrator → " + matchedRoute.topic() + "]: " + result.finalAnswer(),
                result.generatedMessages(),
                result.metrics(),
                result.stopReason(),
                result.structuredOutput()
            );
        }

        var result = defaultAgent.execute(sharedSessionId, prompt);
        return new AgentResult(
            sharedSessionId,
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
