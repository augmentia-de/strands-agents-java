package com.strands.agents.core;

import com.strands.agents.core.internal.ChatMessageConverter;
import com.strands.agents.core.model.agent.*;
import com.strands.agents.core.model.event.*;
import com.strands.agents.core.model.message.Message;
import com.strands.agents.core.model.session.Session;
import com.strands.agents.core.model.tool.ToolCall;
import com.strands.agents.core.model.tool.ToolExecutionResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.UUID;

public class StrandsAgent implements Agent {

    static final int MAX_TOOL_ITERATIONS = 10;

    private final ChatModel model;
    private final ChatMemory chatMemory;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final String sessionId;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private AgentEventListener eventListener;

    public StrandsAgent(ChatModel model) {
        this(model, new ToolRegistry(), new ToolExecutor(), null, null);
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this(model, toolRegistry, toolExecutor, null, null);
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager) {
        this(model, toolRegistry, toolExecutor, conversationManager, null);
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager, SessionManager sessionManager) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.conversationManager = conversationManager;
        this.sessionManager = sessionManager;
        this.chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(conversationManager instanceof SlidingWindowConversationManager sw
                ? sw.windowSize() : 20)
            .build();
        this.sessionId = UUID.randomUUID().toString();
    }

    public void setEventListener(AgentEventListener eventListener) {
        this.eventListener = eventListener;
    }

    @Override
    public AgentResult execute(String prompt) {
        return execute(prompt, Map.of());
    }

    public AgentResult execute(String sessionId, String prompt) {
        return execute(sessionId, prompt, Map.of());
    }

    public AgentResult execute(String prompt, Map<String, Object> contextVariables) {
        return executeWithSession(sessionId, prompt, contextVariables, false);
    }

    public AgentResult execute(String sessionId, String prompt, Map<String, Object> contextVariables) {
        return executeWithSession(sessionId, prompt, contextVariables, true);
    }

    private AgentResult executeWithSession(String sid, String prompt, Map<String, Object> contextVariables,
                                           boolean useSessionManager) {
        if (useSessionManager && sessionManager != null) {
            var session = sessionManager.loadSession(sid)
                .orElseGet(() -> sessionManager.createSession("default", Map.of()));
            if (chatMemory instanceof MessageWindowChatMemory mwcm) {
                mwcm.clear();
                ChatMessageConverter.toLangChain4jMessages(session.messages())
                    .forEach(mwcm::add);
            }
        }

        var result = executeLoop(sid, prompt, contextVariables);

        if (useSessionManager && sessionManager != null) {
            var messages = ChatMessageConverter.toDomainMessages(chatMemory.messages());
            var loaded = sessionManager.loadSession(sid);
            loaded.ifPresent(session -> {
                var status = result.stopReason() == StopReason.ERROR
                    ? AgentStatus.FAILED : AgentStatus.COMPLETED;
                var newState = new AgentState(sid, messages, contextVariables, status);
                var updated = new Session(sid, session.agentName(), messages, newState,
                    session.metadata(), session.createdAt(), Instant.now());
                sessionManager.saveSession(updated);
            });
        }

        return result;
    }

    private AgentResult executeLoop(String sid, String prompt, Map<String, Object> contextVariables) {
        var start = System.nanoTime();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int toolCallCount = 0;

        fire(new AgentStartedEvent(sid, Instant.now(), prompt));

        chatMemory.add(UserMessage.from(prompt));

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            var currentMessages = chatMemory.messages();
            var domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);

            if (conversationManager != null) {
                domainMessages = conversationManager.prune(domainMessages);
                var prunedLangChain = ChatMessageConverter.toLangChain4jMessages(domainMessages);
                if (chatMemory instanceof MessageWindowChatMemory mwcm) {
                    mwcm.clear();
                    for (var msg : prunedLangChain) {
                        mwcm.add(msg);
                    }
                }
                currentMessages = prunedLangChain;
            }

            fire(new ModelRequestedEvent(sid, Instant.now(), domainMessages));

            var requestBuilder = ChatRequest.builder()
                .messages(currentMessages);

            var toolSpecs = toolRegistry.getSpecifications();
            if (!toolSpecs.isEmpty()) {
                requestBuilder.toolSpecifications(toolSpecs);
            }

            ChatResponse response = model.chat(requestBuilder.build());
            AiMessage aiMessage = response.aiMessage();

            chatMemory.add(aiMessage);

            if (response.tokenUsage() != null) {
                totalInputTokens += response.tokenUsage().inputTokenCount();
                totalOutputTokens += response.tokenUsage().outputTokenCount();
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var generatedMessages = ChatMessageConverter.toDomainMessages(chatMemory.messages());

                var result = new AgentResult(
                    sid,
                    aiMessage.text(),
                    generatedMessages,
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.COMPLETED
                );
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }

            toolCallCount += aiMessage.toolExecutionRequests().size();

            for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                fire(new ToolExecutionStartedEvent(sid, Instant.now(),
                    new ToolCall(req.id(), req.name(), req.arguments())));
            }

            try {
                Callable<List<ToolExecutionResult>> execAll = () ->
                    toolExecutor.executeAll(aiMessage.toolExecutionRequests(), toolRegistry);
                List<ToolExecutionResult> results = contextVariables.isEmpty()
                    ? execAll.call()
                    : ScopedValue.where(AgentContext.SESSION, contextVariables).call(execAll);

                for (ToolExecutionResult r : results) {
                    var request = findRequest(aiMessage.toolExecutionRequests(), r.toolName());
                    if (request != null) {
                        chatMemory.add(ToolExecutionResultMessage.from(request, r.result()));
                    }
                    fire(new ToolExecutionFinishedEvent(sid, Instant.now(), r));
                }
            } catch (Exception e) {
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var result = new AgentResult(
                    sid,
                    "Tool-Fehler: " + e.getMessage(),
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.ERROR
                );
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }
        }

        var durationMs = (System.nanoTime() - start) / 1_000_000;
        var result = new AgentResult(
            sid,
            "Maximale Iterationen erreicht",
            ChatMessageConverter.toDomainMessages(chatMemory.messages()),
            new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.MAX_ITERATIONS
        );
        fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
        return result;
    }

    private void fire(AgentEvent event) {
        if (eventListener != null) {
            eventListener.onEvent(event);
        }
    }

    private ToolExecutionRequest findRequest(List<ToolExecutionRequest> requests, String toolName) {
        return requests.stream()
            .filter(r -> r.name().equals(toolName))
            .findFirst()
            .orElse(null);
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
