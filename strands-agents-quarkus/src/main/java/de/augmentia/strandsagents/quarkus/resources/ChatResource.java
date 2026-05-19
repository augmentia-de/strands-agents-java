package de.augmentia.strandsagents.quarkus.resources;

import de.augmentia.strandsagents.quarkus.dto.AgentInitRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatResponse;
import de.augmentia.strandsagents.quarkus.dto.ToolInfo;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {

    @Inject
    AgentService agentService;

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
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response chatStream(ChatRequest req) {
        if (req.prompt == null || req.prompt.isBlank()) {
            return Response.ok("data: {\"error\":\"prompt leer\"}\n\n").build();
        }

        var output = new StringBuilder();
        var phasesRef = new CopyOnWriteArrayList<List<String>>();

        var thread = new Thread(() -> {
            agentService.chatSse(req,
                token -> {
                    synchronized (output) {
                        output.append("data: ").append(toJson("token", token)).append("\n\n");
                    }
                },
                phases -> {
                    synchronized (output) {
                        output.append("data: ").append(toJson("phases", phases.toString())).append("\n\n");
                    }
                },
                result -> {
                    synchronized (output) {
                        output.append("data: ").append(toJson("result", serializeResult(result))).append("\n\n");
                        output.append("data: [DONE]\n\n");
                    }
                }
            );
        });
        thread.start();

        return Response.ok(new java.io.InputStream() {
            private int pos = 0;

            @Override
            public int read() {
                while (true) {
                    synchronized (output) {
                        if (pos < output.length()) {
                            return output.charAt(pos++);
                        }
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return -1; }
                }
            }
        }, MediaType.SERVER_SENT_EVENTS).build();
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

    private String toJson(String key, String value) {
        return "{\"" + key + "\":\"" + value.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
    }

    private String serializeResult(ChatResponse r) {
        return "{\"answer\":\"" + r.answer.replace("\"", "\\\"").replace("\n", "\\n")
            + "\",\"sessionId\":\"" + r.sessionId
            + "\",\"stopReason\":\"" + r.stopReason
            + "\",\"durationMs\":" + r.durationMs
            + ",\"inputTokens\":" + r.inputTokens
            + ",\"outputTokens\":" + r.outputTokens
            + ",\"toolCalls\":" + r.toolCalls
            + "}";
    }
}
