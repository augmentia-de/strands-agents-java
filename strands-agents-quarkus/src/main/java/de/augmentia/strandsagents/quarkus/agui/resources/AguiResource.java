package de.augmentia.strandsagents.quarkus.agui.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.StreamingAgent;
import de.augmentia.strandsagents.quarkus.agui.dto.AguiEvent;
import de.augmentia.strandsagents.quarkus.agui.dto.RunAgentInput;
import de.augmentia.strandsagents.quarkus.agui.service.AguiTranslator;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.UUID;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AguiResource {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] DONE_MARKER = "data: [DONE]\n\n".getBytes();

    @Inject
    AgentService agentService;

    @POST
    @Path("/ag-ui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response agUiChat(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/agentic_chat/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response agenticChat(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/shared_state/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response sharedState(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/tool_based_generative_ui/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response toolBasedGenerativeUi(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/human_in_the_loop/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response humanInTheLoop(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/agentic_generative_ui/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response agenticGenerativeUi(RunAgentInput input) {
        return handleAgui(agentService, input);
    }

    @POST
    @Path("/sse/{agentId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response streamData(@PathParam("agentId") String agentId, RunAgentInput input) {
        return handleAgui(agentService, input, agentId);
    }

    private Response handleAgui(AgentService agentService, RunAgentInput input) {
        return handleAgui(agentService, input, null);
    }

    private Response handleAgui(AgentService agentService, RunAgentInput input, String agentId) {
        if (input == null) input = new RunAgentInput();
        if (input.threadId == null || input.threadId.isBlank()) input.threadId = UUID.randomUUID().toString();
        if (input.runId == null || input.runId.isBlank()) input.runId = UUID.randomUUID().toString();
        if (agentId != null && !agentId.isBlank()) input.threadId = agentId;

        var prompt = extractPrompt(input);
        var translator = new AguiTranslator(input.threadId, input.runId);
        var outputBuffer = new StringBuilder();

        var thread = Thread.ofVirtual().start(() -> {
            try {
                var model = agentService.getStreamingModel();
                if (model == null) {
                    var mockModel = new de.augmentia.strandsagents.core.MockStreamingChatModel();
                    var agent = new StreamingAgent(mockModel,
                        agentService.getFullRegistry(), new de.augmentia.strandsagents.core.DefaultToolExecutor());
                    agent.setEventListener(translator);
                    agent.executeStreaming(prompt, translator);
                } else {
                    var agent = new StreamingAgent(model,
                        agentService.getFullRegistry(), new de.augmentia.strandsagents.core.DefaultToolExecutor(),
                        null, agentService.getSessionManager(), null);
                    agent.setEventListener(translator);
                    agent.executeStreaming(prompt, translator);
                }
            } catch (Exception e) {
                translator.onError(e);
            }
        });

        return Response.ok(new InputStream() {
            private int pos = 0;
            private boolean done;

            @Override
            public int read() {
                while (!done) {
                    flushQueue();
                    synchronized (outputBuffer) {
                        if (pos < outputBuffer.length()) {
                            return outputBuffer.charAt(pos++) & 0xFF;
                        }
                    }
                    if (translator.isClosed()) {
                        flushQueue();
                        synchronized (outputBuffer) {
                            if (pos < outputBuffer.length()) {
                                return outputBuffer.charAt(pos++) & 0xFF;
                            }
                        }
                        synchronized (outputBuffer) {
                            outputBuffer.append(new String(DONE_MARKER));
                        }
                        done = true;
                        try { thread.join(1000); } catch (InterruptedException ignored) {}
                        synchronized (outputBuffer) {
                            return pos < outputBuffer.length() ? outputBuffer.charAt(pos++) & 0xFF : -1;
                        }
                    }
                    try { Thread.sleep(50); } catch (InterruptedException e) { return -1; }
                }
                return -1;
            }

            private void flushQueue() {
                var q = translator.eventQueue();
                while (true) {
                    var event = q.poll();
                    if (event == null) break;
                    try {
                        var json = JSON.writeValueAsString(event);
                        synchronized (outputBuffer) {
                            outputBuffer.append("data: ").append(json).append("\n\n");
                        }
                    } catch (Exception e) {
                        synchronized (outputBuffer) {
                            outputBuffer.append("data: {\"type\":\"RUN_ERROR\",\"message\":\"")
                                .append(e.getMessage().replace("\"", "\\\"")).append("\"}\n\n");
                        }
                    }
                }
            }
        }, MediaType.SERVER_SENT_EVENTS)
        .header("Cache-Control", "no-cache")
        .build();
    }

    private static String extractPrompt(RunAgentInput input) {
        if (input.messages == null || input.messages.isEmpty()) return "Hello";
        for (int i = input.messages.size() - 1; i >= 0; i--) {
            var msg = input.messages.get(i);
            if ("user".equals(msg.role) && msg.content != null && !msg.content.isBlank()) {
                return msg.content;
            }
        }
        return "Hello";
    }
}
