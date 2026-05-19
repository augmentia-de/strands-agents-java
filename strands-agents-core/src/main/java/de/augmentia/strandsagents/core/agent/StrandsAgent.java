package de.augmentia.strandsagents.core.agent;

import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.LlmConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.context.AgentContext;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.SessionManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.internal.ChatMessageConverter;
import de.augmentia.strandsagents.core.model.agent.*;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.model.message.Message;
import de.augmentia.strandsagents.core.model.session.Session;
import de.augmentia.strandsagents.core.model.tool.ToolCall;
import de.augmentia.strandsagents.core.model.tool.ToolExecutionResult;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.PluginRegistry;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailException;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLPlugin;
import de.augmentia.strandsagents.core.resilience.*;
import de.augmentia.strandsagents.core.structured.StructuredOutputConfig;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StrandsAgent implements Agent {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

    static final int MAX_TOOL_ITERATIONS = 10;
    static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final ChatModel model;
    private final ChatMemory chatMemory;
    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final String sessionId;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private final RetryConfig retryConfig;
    private final CircuitBreaker circuitBreaker;
    private final SubmissionPublisher<AgentEvent> eventPublisher;
    private AgentEventListener eventListener;
    private String systemPrompt = "";
    private final List<Consumer<StringBuilder>> pluginHooks = new ArrayList<>();
    private GuardrailPlugin guardrailPlugin;
    private HITLPlugin hitlPlugin;
    private volatile AgentPhase phase = AgentPhase.IDLE;
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private StructuredOutputConfig structuredOutputConfig;

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
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager,
            ResilienceConfig.DEFAULT);
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager, SessionManager sessionManager,
                        ResilienceConfig resilienceConfig) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.conversationManager = conversationManager;
        this.sessionManager = sessionManager;
        this.retryConfig = resilienceConfig != null ? resilienceConfig.retryConfig() : null;
        this.circuitBreaker = resilienceConfig != null && resilienceConfig.circuitBreakerConfig() != null
            ? new CircuitBreaker(resilienceConfig.circuitBreakerConfig())
            : null;
        this.chatMemory = MessageWindowChatMemory.builder()
            .maxMessages(conversationManager instanceof SlidingWindowConversationManager sw
                ? sw.windowSize() : 20)
            .build();
        this.sessionId = UUID.randomUUID().toString();
        this.eventPublisher = new SubmissionPublisher<>();
    }

    public StrandsAgent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                        ConversationManager conversationManager, SessionManager sessionManager,
                        ResilienceConfig resilienceConfig, List<Plugin> plugins) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig);
        if (plugins != null && !plugins.isEmpty()) {
            for (var p : plugins) {
                if (p instanceof GuardrailPlugin gp) this.guardrailPlugin = gp;
                if (p instanceof HITLPlugin hp) this.hitlPlugin = hp;
            }
            var registry = new PluginRegistry(plugins);
            registry.initialize(this);
            setEventListener(registry);
        }
    }

    public StrandsAgent(AgentConfig config) {
        this(config.modelName() != null
            ? ModelFactory.createOpenAi(LlmConfig.fromEnv(config.modelName()))
            : ModelFactory.createOpenAiFromEnv(),
            config.toolRegistry() != null ? config.toolRegistry() : new ToolRegistry(),
            new ToolExecutor(),
            config.conversationManager(),
            config.sessionManager(),
            config.resilienceConfig() != null ? config.resilienceConfig() : ResilienceConfig.DEFAULT,
            config.plugins());
        if (config.systemPrompt() != null && !config.systemPrompt().isBlank()) {
            setSystemPrompt(config.systemPrompt());
        }
    }

    public void setEventListener(AgentEventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void setStructuredOutputModel(Class<?> modelClass) {
        this.structuredOutputConfig = StructuredOutputConfig.staticModel(modelClass);
    }

    public void setStructuredOutputSchema(String jsonSchema) {
        this.structuredOutputConfig = StructuredOutputConfig.dynamicSchema(jsonSchema);
    }

    public void setStructuredOutputConfig(StructuredOutputConfig config) {
        this.structuredOutputConfig = config;
    }

    public StructuredOutputConfig getStructuredOutputConfig() {
        return structuredOutputConfig;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setPluginHook(Consumer<StringBuilder> hook) {
        pluginHooks.add(hook);
    }

    @Override
    public AgentResult execute(String prompt) {
        return execute(prompt, Map.of());
    }

    @Override
    public CompletableFuture<AgentResult> executeAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> execute(prompt), VIRTUAL_EXECUTOR);
    }

    public CompletableFuture<AgentResult> executeAsync(String prompt, Map<String, Object> contextVariables) {
        return CompletableFuture.supplyAsync(() -> execute(prompt, contextVariables), VIRTUAL_EXECUTOR);
    }

    public Flow.Publisher<AgentEvent> eventStream() {
        return eventPublisher;
    }

    public CompletableFuture<AgentResult> executeEvents(String prompt, Flow.Subscriber<? super AgentEvent> subscriber) {
        eventPublisher.subscribe(subscriber);
        return executeAsync(prompt);
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
        boolean structuredForceAttempted = false;
        boolean structuredForceActive = false;
        String structuredOutputResult = null;

        phase = AgentPhase.EXECUTING;
        fire(new AgentStartedEvent(sid, Instant.now(), prompt));

        chatMemory.add(UserMessage.from(prompt));

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            checkPaused();

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
                domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);
            }

            // Input guardrails
            if (guardrailPlugin != null) {
                var guardResult = runInputGuardrails(domainMessages);
                if (guardResult != null) return guardResult;
            }

            var sb = new StringBuilder(systemPrompt != null ? systemPrompt : "");
            var bie = new BeforeInvocationEvent(sid, Instant.now(), sb, domainMessages);
            fire(bie);
            for (var hook : pluginHooks) {
                hook.accept(sb);
            }
            systemPrompt = sb.toString();

            fire(new ModelRequestedEvent(sid, Instant.now(), domainMessages));

            var toolSpecs = structuredForceActive
                ? List.<dev.langchain4j.agent.tool.ToolSpecification>of()
                : toolRegistry.getSpecifications();

            ChatResponse response;
            try {
                response = callWithResilience(currentMessages, toolSpecs);
            } catch (Exception e) {
                phase = AgentPhase.FAILED;
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var result = new AgentResult(
                    sid,
                    "LLM-Fehler: " + e.getMessage(),
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.ERROR
                );
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }
            AiMessage aiMessage = response.aiMessage();

            // Output guardrails
            var responseText = aiMessage.text() != null ? aiMessage.text() : "";
            fire(new AfterInvocationEvent(sid, Instant.now(), responseText, domainMessages));
            if (guardrailPlugin != null) {
                var guardResult = runOutputGuardrails(responseText, domainMessages);
                if (guardResult != null) return guardResult;
            }

            chatMemory.add(aiMessage);

            if (response.tokenUsage() != null) {
                totalInputTokens += response.tokenUsage().inputTokenCount();
                totalOutputTokens += response.tokenUsage().outputTokenCount();
            }

            // Structured output: try to extract JSON if enabled
            if (structuredOutputConfig != null && structuredOutputConfig.isEnabled()
                && !aiMessage.hasToolExecutionRequests()) {
                try {
                    OBJECT_MAPPER.readTree(responseText);
                    structuredOutputResult = responseText;
                } catch (JsonProcessingException e) {
                    if (!structuredForceAttempted) {
                        structuredForceAttempted = true;
                        structuredForceActive = true;
                        chatMemory.add(UserMessage.from(structuredOutputConfig.forcePrompt()));
                        continue;
                    }
                }
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                phase = AgentPhase.COMPLETED;
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var generatedMessages = ChatMessageConverter.toDomainMessages(chatMemory.messages());

                var result = new AgentResult(
                    sid,
                    responseText,
                    generatedMessages,
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.COMPLETED,
                    structuredOutputResult
                );
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }

            // HITL before tool execution
            if (hitlPlugin != null && hitlPlugin.authority() == HITLAuthority.CONFIRM) {
                phase = AgentPhase.WAITING_FOR_HUMAN;
                fire(new AgentStateChangedEvent(sid, Instant.now(),
                    AgentPhase.EXECUTING, AgentPhase.WAITING_FOR_HUMAN,
                    prompt, iteration, 0));
                var approval = hitlPlugin.provider().requestApproval(
                    "tool-execution",
                    "Tool-Calls: " + aiMessage.toolExecutionRequests().stream()
                        .map(ToolExecutionRequest::name).toList());
                if (!approval.approved()) {
                    phase = AgentPhase.FAILED;
                    var durationMs = (System.nanoTime() - start) / 1_000_000;
                    var result = new AgentResult(
                        sid,
                        "HITL abgelehnt: " + approval.feedback(),
                        ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                        new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                        StopReason.INTERRUPTED
                    );
                    fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                    return result;
                }
                phase = AgentPhase.EXECUTING;
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
                    ? wrapWithRetry(() -> execAll.call())
                    : ScopedValue.where(AgentContext.SESSION, contextVariables)
                        .call(() -> wrapWithRetry(execAll::call));

                for (ToolExecutionResult r : results) {
                    var request = findRequest(aiMessage.toolExecutionRequests(), r.toolName());
                    if (request != null) {
                        chatMemory.add(ToolExecutionResultMessage.from(request, r.result()));
                    }
                    fire(new ToolExecutionFinishedEvent(sid, Instant.now(), r));
                }
            } catch (Exception e) {
                phase = AgentPhase.FAILED;
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

        phase = AgentPhase.FAILED;
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

    private AgentResult runInputGuardrails(List<Message> domainMessages) {
        for (var g : guardrailPlugin.inputGuardrails()) {
            var result = g.validate(domainMessages, guardrailPlugin.name());
            if (!result.pass()) {
                return handleGuardrailBlock(result);
            }
        }
        return null;
    }

    private AgentResult runOutputGuardrails(String response, List<Message> domainMessages) {
        for (var g : guardrailPlugin.outputGuardrails()) {
            var result = g.validate(domainMessages, "output:" + response);
            if (!result.pass()) {
                return handleGuardrailBlock(result);
            }
        }
        return null;
    }

    private AgentResult handleGuardrailBlock(GuardrailResult guardResult) {
        return switch (guardrailPlugin.blockAction()) {
            case THROW -> {
                throw new GuardrailException(guardResult.reason());
            }
            case FALLBACK -> {
                phase = AgentPhase.FAILED;
                fire(new AgentFinishedEvent(sessionId, Instant.now(), guardrailPlugin.fallbackMessage()));
                yield new AgentResult(
                    sessionId,
                    guardrailPlugin.fallbackMessage(),
                    List.of(),
                    new ExecutionMetrics(0, 0, 0, 0),
                    StopReason.ERROR
                );
            }
            case ESCALATE -> {
                if (hitlPlugin != null) {
                    var approval = hitlPlugin.provider().requestApproval(
                        "guardrail-block",
                        "Guardrail: " + guardResult.reason());
                    if (approval.approved()) {
                        yield null; // continue execution
                    }
                }
                phase = AgentPhase.FAILED;
                fire(new AgentFinishedEvent(sessionId, Instant.now(), guardrailPlugin.fallbackMessage()));
                yield new AgentResult(
                    sessionId,
                    guardrailPlugin.fallbackMessage(),
                    List.of(),
                    new ExecutionMetrics(0, 0, 0, 0),
                    StopReason.ERROR
                );
            }
        };
    }

    protected ChatResponse doChat(ChatRequest request) {
        return model.chat(request);
    }

    private ChatResponse callWithResilience(List<ChatMessage> currentMessages, List<ToolSpecification> toolSpecs) {
        var recovery = new TokenRecovery();
        var msgs = currentMessages;

        while (true) {
            try {
                var request = buildRequest(msgs, toolSpecs);

                Callable<ChatResponse> chatCall = () -> doChat(request);

                if (circuitBreaker != null) {
                    RetryConfig cfg = effectiveRetryConfig();
                    return circuitBreaker.call(
                        () -> Retry.run(chatCall, cfg),
                        () -> { throw new RuntimeException("CircuitBreaker: Service temporär nicht verfügbar"); });
                }

                return Retry.run(chatCall, effectiveRetryConfig());
            } catch (Exception e) {
                if (TokenRecovery.isTokenLimitError(e) && recovery.recover(chatMemory)) {
                    msgs = chatMemory.messages();
                    continue;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException("LLM call failed", e);
            }
        }
    }

    private ChatRequest buildRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecs) {
        var builder = ChatRequest.builder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            var allMessages = new ArrayList<ChatMessage>();
            allMessages.add(SystemMessage.from(systemPrompt));
            allMessages.addAll(messages);
            builder.messages(allMessages);
        } else {
            builder.messages(messages);
        }
        if (toolSpecs != null && !toolSpecs.isEmpty()) {
            builder.toolSpecifications(toolSpecs);
        }
        if (structuredOutputConfig != null && structuredOutputConfig.isEnabled()) {
            var schemaStr = structuredOutputConfig.effectiveSchema();
            if (schemaStr != null) {
                var rawSchema = JsonRawSchema.from(schemaStr);
                var jsonSchema = JsonSchema.builder()
                    .name(structuredOutputConfig.mode().name())
                    .rootElement(rawSchema)
                    .build();
                builder.responseFormat(ResponseFormat.builder()
                    .type(ResponseFormatType.JSON)
                    .jsonSchema(jsonSchema)
                    .build());
            }
        }
        return builder.build();
    }

    private <T> T wrapWithRetry(Callable<T> callable) throws Exception {
        if (retryConfig != null) {
            return Retry.run(callable, retryConfig);
        }
        return callable.call();
    }

    private RetryConfig effectiveRetryConfig() {
        return retryConfig != null ? retryConfig : RetryConfig.DEFAULT;
    }

    protected void fire(AgentEvent event) {
        if (eventListener != null) {
            eventListener.onEvent(event);
        }
        eventPublisher.submit(event);
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

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public AgentPhase getPhase() {
        return phase;
    }

    public void pauseExecution() {
        phase = AgentPhase.WAITING_FOR_HUMAN;
    }

    public void resumeExecution() {
        pauseLock.lock();
        try {
            phase = AgentPhase.EXECUTING;
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    public void approve() {
        resumeExecution();
    }

    public void reject(String reason) {
        pauseLock.lock();
        try {
            phase = AgentPhase.FAILED;
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    private void checkPaused() {
        if (phase == AgentPhase.WAITING_FOR_HUMAN) {
            pauseLock.lock();
            try {
                while (phase == AgentPhase.WAITING_FOR_HUMAN) {
                    pauseCondition.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("HITL interrupted", e);
            } finally {
                pauseLock.unlock();
            }
            if (phase == AgentPhase.FAILED) {
                throw new RuntimeException("HITL rejected");
            }
        }
    }
}
