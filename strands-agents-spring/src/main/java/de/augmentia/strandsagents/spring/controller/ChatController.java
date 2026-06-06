package de.augmentia.strandsagents.spring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.model.event.AgentFinishedEvent;
import de.augmentia.strandsagents.core.model.event.AgentStartedEvent;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.model.event.TokenEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;
import de.augmentia.strandsagents.core.model.agent.AgentState;
import de.augmentia.strandsagents.core.model.agent.AgentStatus;
import de.augmentia.strandsagents.core.model.session.Session;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.sessions.SessionManager;
import dev.langchain4j.model.chat.StreamingChatModel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are an experienced software developer with a background in ethnology.
        You approach problems with both technical precision and cultural awareness,
        considering how software systems affect human communities and practices.

        # Communication Style
        - Be concise and direct in technical matters, but considerate of context
        - Explain complex concepts clearly without unnecessary jargon
        - Acknowledge uncertainty when appropriate

        # Available Tools
        You have access to the following tools that you can use when needed:

        ## web_search
        Searches the web using a search engine. Use this to find current information,
        documentation, or resources. Provide a clear query string.
        - Query: the search terms (string)

        ## web_fetch
        Fetches and returns the content of a given URL. Use this after web_search to
        retrieve the full content of a page you want to read or reference.
        - URL: the web address to fetch (string)

        # How to Use Tools
        - When you need current or factual information beyond your training data,
          use web_search first, then web_fetch on the most relevant result
        - Explain to the user why you are searching before making a tool call
        - Synthesize the information you retrieve rather than dumping raw content
        - If a search returns no useful results, try a different query and inform
          the user

        # Response Format
        - Use markdown for formatting when helpful (lists, code blocks, emphasis)
        - For code, always specify the language in code blocks
        - When analyzing problems, structure your thinking: observe → analyze →
          recommend
        """ .trim();

    private final StreamingChatModel model;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private final ResilienceConfig resilienceConfig;

    public ChatController(
            StreamingChatModel model,
            ToolRegistry toolRegistry,
            ToolExecutor toolExecutor,
            ConversationManager conversationManager,
            SessionManager sessionManager,
            ResilienceConfig resilienceConfig) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.conversationManager = conversationManager;
        this.sessionManager = sessionManager;
        this.resilienceConfig = resilienceConfig;
    }

    @PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody Map<String, String> body) {
        var prompt = body.get("prompt");
        var sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        log.info(">>> /api/chat/stream — sessionId={}, prompt='{}'", sessionId, prompt);
        var emitter = new SseEmitter(0L);

        Thread.startVirtualThread(() -> {
            var agent = new StreamingAgent(model, toolRegistry, toolExecutor,
                conversationManager, sessionManager, resilienceConfig);

            agent.addEventListener(event -> {
                try {
                    switch (event) {
                        case AgentStartedEvent e -> {
                            var data = MAPPER.writeValueAsString(Map.of("prompt", e.initialPrompt()));
                            log.debug("SSE > started");
                            emitter.send(SseEmitter.event().name("started").data(data));
                        }
                        case TokenEvent e -> {
                            log.trace("SSE > token: '{}'", e.token());
                            emitter.send(SseEmitter.event().name("token").data(MAPPER.writeValueAsString(e.token())));
                        }
                        case AgentStateChangedEvent e -> {
                            var data = MAPPER.writeValueAsString(Map.of("phase", e.currentPhase().name(), "goal", e.goal()));
                            log.debug("SSE > phase: {}", e.currentPhase().name());
                            emitter.send(SseEmitter.event().name("phase").data(data));
                        }
                        case ToolExecutionStartedEvent e -> {
                            var data = MAPPER.writeValueAsString(Map.of("name", e.toolCall().toolName(), "arguments", e.toolCall().arguments()));
                            log.debug("SSE > tool_start: {}", e.toolCall().toolName());
                            emitter.send(SseEmitter.event().name("tool_start").data(data));
                        }
                        case ToolExecutionFinishedEvent e -> {
                            var data = MAPPER.writeValueAsString(Map.of("name", e.result().toolName(), "result", e.result().result(), "error", e.result().isError()));
                            log.debug("SSE > tool_end: {} (error={})", e.result().toolName(), e.result().isError());
                            emitter.send(SseEmitter.event().name("tool_end").data(data));
                        }
                        case AgentFinishedEvent e -> {
                            var data = MAPPER.writeValueAsString(Map.of("answer", e.finalAnswer()));
                            log.debug("SSE > finished");
                            emitter.send(SseEmitter.event().name("finished").data(data));
                        }
                        default -> {}
                    }
                } catch (Exception ex) {
                    log.debug("Client disconnected while sending event: {}", ex.getMessage());
                }
            });

            agent.setSystemPrompt(SYSTEM_PROMPT);

            try {
                // Ensure session exists with the correct sessionId
                // (FileSessionManager.createSession generates a random UUID, not the requested one)
                if (sessionManager.loadSession(sessionId).isEmpty()) {
                    var now = Instant.now();
                    var state = new AgentState(sessionId, List.of(), Map.of(), AgentStatus.IDLE);
                    var session = new Session(sessionId, "default", List.of(), state, Map.of(), now, now);
                    sessionManager.saveSession(session);
                    log.debug("Session created: {}", sessionId);
                }

                var result = agent.executeStreaming(sessionId, prompt, null);
                var data = MAPPER.writeValueAsString(Map.of(
                    "answer", result.finalAnswer(),
                    "sessionId", sessionId));
                try {
                    emitter.send(SseEmitter.event().name("done").data(data));
                } catch (Exception ex) {
                    log.debug("Client disconnected before done event: {}", ex.getMessage());
                }
                try {
                    emitter.complete();
                } catch (Exception ex) {
                    log.debug("Emitter already closed: {}", ex.getMessage());
                }
                log.info("Chat completed — sessionId={}", sessionId);
            } catch (Exception e) {
                log.error("Chat failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                        .data(MAPPER.writeValueAsString(Map.of("error", e.getMessage()))));
                    emitter.complete();
                } catch (Exception ex) {
                    log.debug("Client disconnected during error send: {}", ex.getMessage());
                }
            }
        });

        return emitter;
    }
}
