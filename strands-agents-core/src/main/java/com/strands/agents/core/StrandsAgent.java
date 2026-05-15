package com.strands.agents.core;

import com.strands.agents.core.internal.ChatMessageConverter;
import com.strands.agents.core.model.agent.*;
import com.strands.agents.core.model.event.*;
import com.strands.agents.core.model.message.Message;
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
    private AgentEventListener eventListener;

    public StrandsAgent(ChatModel model) {
        this(model, new ToolRegistry(), new ToolExecutor());
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(20)
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

    public AgentResult execute(String prompt, Map<String, Object> contextVariables) {
        var start = System.nanoTime();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int toolCallCount = 0;

        fire(new AgentStartedEvent(sessionId, Instant.now(), prompt));

        chatMemory.add(UserMessage.from(prompt));

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            var currentMessages = chatMemory.messages();
            var domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);
            fire(new ModelRequestedEvent(sessionId, Instant.now(), domainMessages));

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
                    sessionId,
                    aiMessage.text(),
                    generatedMessages,
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.COMPLETED
                );
                fire(new AgentFinishedEvent(sessionId, Instant.now(), result.finalAnswer()));
                return result;
            }

            toolCallCount += aiMessage.toolExecutionRequests().size();

            for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                fire(new ToolExecutionStartedEvent(sessionId, Instant.now(),
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
                    fire(new ToolExecutionFinishedEvent(sessionId, Instant.now(), r));
                }
            } catch (Exception e) {
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var result = new AgentResult(
                    sessionId,
                    "Tool-Fehler: " + e.getMessage(),
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.ERROR
                );
                fire(new AgentFinishedEvent(sessionId, Instant.now(), result.finalAnswer()));
                return result;
            }
        }

        var durationMs = (System.nanoTime() - start) / 1_000_000;
        var result = new AgentResult(
            sessionId,
            "Maximale Iterationen erreicht",
            ChatMessageConverter.toDomainMessages(chatMemory.messages()),
            new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.MAX_ITERATIONS
        );
        fire(new AgentFinishedEvent(sessionId, Instant.now(), result.finalAnswer()));
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
