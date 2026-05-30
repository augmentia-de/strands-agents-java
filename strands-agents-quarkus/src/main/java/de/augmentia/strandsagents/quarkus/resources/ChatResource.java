package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.dto.AgentInitRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatResponse;
import de.augmentia.strandsagents.quarkus.dto.ToolInfo;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    AgentService agentService;

    @Inject
    ObjectMapper nativeJackson;

    @POST
    @Path("/chat")
    public ChatResponse chat(ChatRequest req) {
        if (req.prompt == null || req.prompt.isBlank()) {
            var err = new ChatResponse();
            err.error = "prompt darf nicht leer sein";
            return err;
        }
        return agentService.chat(req);
    }

    @POST
    @Path("/chat/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS) // Quarkus automatically wraps Multi into SSE chunk formatting
    @Blocking // Offloads long-running LLM stream connection from the reactive IO loop
    public Multi<? extends Object> chatStream(ChatRequest req) {
        if (req.prompt == null || req.prompt.isBlank()) {
            return Multi.createFrom().item("{\"error\":\"prompt leer\"}");
        }

        return Multi.createFrom().<String>emitter(emitter -> {
            try {
                agentService.chatSse(req,
                        token -> {
                            if (token != null) {
                                try {
                                    emitter.emit(nativeJackson.writeValueAsString(Map.of("token", token)));
                                } catch (Exception e) {
                                    // skip malformed token
                                }
                            }
                        },
                        phases -> {
                            if (phases != null) {
                                try {
                                    emitter.emit(nativeJackson.writeValueAsString(Map.of("phases", phases)));
                                } catch (Exception e) {
                                    // skip malformed phases
                                }
                            }
                        },
                        result -> {
                            if (result != null) {
                                try {
                                    emitter.emit(nativeJackson.writeValueAsString(Map.of("result", result)));
                                } catch (Exception e) {
                                    // skip malformed result
                                }
                            }
                            emitter.emit("[DONE]");
                            emitter.complete();
                        }
                );
            } catch (Exception e) {
                try {
                    emitter.emit(nativeJackson.writeValueAsString(Map.of("error", e.getMessage())));
                } catch (Exception ex) {
                    // skip
                }
                emitter.fail(e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @POST
    @Path("/agent/init")
    public ChatResponse initAgent(AgentInitRequest req) {
        if (req.tools == null) req.tools = List.of();
        if (req.skills == null) req.skills = List.of();
        return agentService.initAgent(req);
    }

    @POST
    @Path("/mcp/discover")
    public List<ToolInfo> discoverMcpTools(Map<String, String> body) {
        var url = body != null ? body.get("url") : null;
        if (url == null || url.isBlank()) {
            return List.of();
        }
        return agentService.discoverMcpTools(url);
    }

    @POST
    @Path("/agent/release")
    public Response releaseSession(Map<String, String> body) {
        var sessionId = body != null ? body.get("sessionId") : null;
        if (sessionId != null) {
            agentService.releaseSession(sessionId);
        }
        return Response.ok(Map.of("released", sessionId)).build();
    }

    private String serializeResult(ChatResponse r) {
        try {
            return nativeJackson.writeValueAsString(Map.of("result", r));
        } catch (Exception e) {
            return "{\"error\":\"serialization failed\"}";
        }
    }
}