package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.service.AgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

@Path("/api/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    @Inject
    AgentService agentService;

    @GET
    public List<Map<String, Object>> listSessions() {
        return agentService.getSessionManager().listSessions(null).stream()
            .map(s -> Map.<String, Object>of(
                "id", s.sessionId(),
                "agentName", s.agentName(),
                "createdAt", s.createdAt().toString(),
                "updatedAt", s.updatedAt().toString(),
                "messageCount", s.messages().size()
            ))
            .toList();
    }

    @GET
    @Path("/{id}")
    public Map<String, Object> getSession(@PathParam("id") String id) {
        var session = agentService.getSessionManager().loadSession(id);
        return session.map(s -> Map.<String, Object>of(
                "id", s.sessionId(),
                "agentName", s.agentName(),
                "createdAt", s.createdAt().toString(),
                "updatedAt", s.updatedAt().toString(),
                "messages", s.messages().stream()
                    .map(m -> Map.of(
                        "role", m.getClass().getSimpleName().replace("Message", ""),
                        "content", m.content()
                    ))
                    .toList(),
                "status", s.state() != null ? s.state().status().name() : "UNKNOWN"
            ))
            .orElse(Map.of("error", "Session nicht gefunden"));
    }

    @DELETE
    @Path("/{id}")
    public Map<String, Object> deleteSession(@PathParam("id") String id) {
        agentService.getSessionManager().deleteSession(id);
        return Map.of("deleted", id);
    }
}
