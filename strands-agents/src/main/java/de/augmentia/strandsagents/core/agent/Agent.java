package de.augmentia.strandsagents.core.agent;

import de.augmentia.strandsagents.core.AgentEventListener;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.LlmConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.context.AgentContext;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.internal.ChatMessageConverter;
import de.augmentia.strandsagents.core.model.agent.*;
import de.augmentia.strandsagents.core.model.event.*;
import de.augmentia.strandsagents.core.model.message.Message;
import de.augmentia.strandsagents.core.model.session.Session;
import de.augmentia.strandsagents.core.model.tool.ToolCall;
import de.augmentia.strandsagents.core.model.tool.ToolExecutionResult;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookRegistry;
import de.augmentia.strandsagents.core.hook.HookResult;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.PluginRegistry;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailException;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.plugin.hitl.HITLPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointService;
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
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The core Agent class that orchestrates interactions between a LLM, tools, and plugins.
 * <p>
 * The Agent handles the main loop of LLM communication, including tool call processing,
 * session management, conversation memory management, and plugin execution (guardrails, HITL).
 * It supports both simple synchronous execution and more complex scenarios with resilience
 * and state management.
 * </p>
 */
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final int LOG_MAX = 2000;

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= LOG_MAX ? s : s.substring(0, LOG_MAX) + "...";
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static int MAX_TOOL_ITERATIONS = 10;
    static final int MAX_HOOK_RETRIES = 3;
    static final int DEFAULT_MAX_MESSAGES = 20;
    static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final ChatModel model;
    private final ChatMemory chatMemory;
    private ToolRegistry toolRegistry;
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
    private CheckpointService checkpointService;
    private volatile AgentPhase phase = AgentPhase.IDLE;
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private StructuredOutputConfig structuredOutputConfig;
    private HookRegistry hookRegistry;
    private final ChatMemoryStore chatMemoryStore;

    /**
     * Constructs an Agent with only a chat model and default components.
     *
     * @param model the chat model to use for orchestration
     */
    public Agent(ChatModel model) {
        this(model, new ToolRegistry(), new ToolExecutor(), null, null);
    }

    /**
     * Constructs an Agent with model, tool registry, and tool executor.
     *
     * @param model        the chat model to use
     * @param toolRegistry the registry of available tools
     * @param toolExecutor the executor for running tool calls
     */
    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this(model, toolRegistry, toolExecutor, null, null);
    }

    /**
     * Constructs an Agent with advanced conversation management.
     *
     * @param model               the chat model to use
     * @param toolRegistry        the registry of available tools
     * @param toolExecutor        the executor for running tool calls
     * @param conversationManager manager for chat history and context window
     */
    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager) {
        this(model, toolRegistry, toolExecutor, conversationManager, null);
    }

    /**
     * Constructs an Agent with session management.
     *
     * @param model               the chat model to use
     * @param toolRegistry        the registry of available tools
     * @param toolExecutor        the executor for running tool calls
     * @param conversationManager manager for chat history and context window
     * @param sessionManager      manager for persistent sessions
     */
    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager,
            ResilienceConfig.DEFAULT);
    }

    /**
     * Constructs an Agent with resilience configuration.
     *
     * @param model               the chat model to use
     * @param toolRegistry        the registry of available tools
     * @param toolExecutor        the executor for running tool calls
     * @param conversationManager manager for chat history and context window
     * @param sessionManager      manager for persistent sessions
     * @param resilienceConfig    configuration for retries and circuit breakers
     */
    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ResilienceConfig resilienceConfig) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, null, resilienceConfig);
    }

    /**
     * Full constructor for the Agent class.
     *
     * @param model               the chat model to use
     * @param toolRegistry        the registry of available tools
     * @param toolExecutor        the executor for running tool calls
     * @param conversationManager manager for chat history and context window
     * @param sessionManager      manager for persistent sessions
     * @param chatMemoryStore     custom storage for chat memory
     * @param resilienceConfig    configuration for retries and circuit breakers
     */
    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig) {
        this.model = model;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.conversationManager = conversationManager;
        this.sessionManager = sessionManager;
        this.retryConfig = resilienceConfig != null ? resilienceConfig.retryConfig() : null;
        this.circuitBreaker = resilienceConfig != null && resilienceConfig.circuitBreakerConfig() != null
            ? new CircuitBreaker(resilienceConfig.circuitBreakerConfig())
            : null;
        this.chatMemoryStore = chatMemoryStore;
        var chatMemoryBuilder = MessageWindowChatMemory.builder()
            .maxMessages(conversationManager instanceof SlidingWindowConversationManager sw
                ? sw.windowSize() : DEFAULT_MAX_MESSAGES);
        if (chatMemoryStore != null) {
            chatMemoryBuilder.chatMemoryStore(chatMemoryStore);
            log.debug("ChatMemoryStore: {}", chatMemoryStore.getClass().getSimpleName());
        }
        this.chatMemory = chatMemoryBuilder.build();
        this.sessionId = UUID.randomUUID().toString();
        this.eventPublisher = new SubmissionPublisher<>();
        this.hookRegistry = new HookRegistry();
        log.debug("Agent created — model={}, tools={}, convMgr={}, sessionMgr={}, resilience={}",
            model.getClass().getSimpleName(),
            toolRegistry != null ? toolRegistry.size() : 0,
            conversationManager != null ? conversationManager.getClass().getSimpleName() : "none",
            sessionManager != null ? sessionManager.getClass().getSimpleName() : "none",
            resilienceConfig != null ? "enabled" : "none");
    }

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ResilienceConfig resilienceConfig, HookRegistry hookRegistry) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig);
        this.hookRegistry = hookRegistry != null ? hookRegistry : new HookRegistry();
    }

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
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

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ResilienceConfig resilienceConfig, List<Plugin> plugins, HookRegistry hookRegistry) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig, hookRegistry);
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

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
                 List<Plugin> plugins) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, chatMemoryStore, resilienceConfig);
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

    public Agent(AgentConfig config) {
        this(config.modelName() != null
            ? ModelFactory.createOpenAi(LlmConfig.fromEnv(config.modelName()))
            : ModelFactory.createOpenAiFromEnv(),
            config.toolRegistry() != null ? config.toolRegistry() : new ToolRegistry(),
            new ToolExecutor(),
            config.conversationManager(),
            config.sessionManager(),
            config.chatMemoryStore(),
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

    /**
     * Executes a prompt in the current default session.
     *
     * @param prompt the user input text
     * @return the result of the agent execution
     */
    public AgentResult execute(String prompt) {
        log.debug("execute() — prompt={}", truncate(prompt));
        return execute(prompt, Map.of());
    }

    /**
     * Executes a prompt with additional context variables.
     *
     * @param prompt           the user input text
     * @param contextVariables map of variables for the execution context
     * @return the result of the agent execution
     */
    public AgentResult execute(String prompt, Map<String, Object> contextVariables) {
        log.debug("execute(prompt={}, contextVariables={})", truncate(prompt), contextVariables);
        return executeWithSession(sessionId, prompt, contextVariables, false);
    }

    /**
     * Executes a prompt within a specific session.
     *
     * @param sessionId unique identifier for the session
     * @param prompt    the user input text
     * @return the result of the agent execution
     */
    public AgentResult execute(String sessionId, String prompt) {
        log.debug("execute(sessionId={}, prompt={})", sessionId, truncate(prompt));
        return execute(sessionId, prompt, Map.of());
    }

    /**
     * Full execution method with session ID, prompt, and context variables.
     * <p>
     * This is the main entry point for agent orchestration. It handles session loading,
     * conversation history management, and the core reasoning loop.
     * </p>
     *
     * @param sessionId        unique identifier for the session
     * @param prompt           the user input text
     * @param contextVariables map of variables for the execution context
     * @return the result of the agent execution
     */
    public AgentResult execute(String sessionId, String prompt, Map<String, Object> contextVariables) {
        log.debug("execute(sessionId={}, prompt={}, contextVariables={})", sessionId, truncate(prompt), contextVariables);
        return executeWithSession(sessionId, prompt, contextVariables, true);
    }

    public CompletableFuture<AgentResult> executeAsync(String prompt) {
        log.debug("executeAsync(prompt={})", truncate(prompt));
        return executeAsync(prompt, Map.of());
    }

    public CompletableFuture<AgentResult> executeAsync(String prompt, Map<String, Object> contextVariables) {
        log.debug("executeAsync(prompt={}, contextVariables={})", truncate(prompt), contextVariables);
        return CompletableFuture.supplyAsync(() -> execute(prompt, contextVariables), VIRTUAL_EXECUTOR);
    }

    public Flow.Publisher<AgentEvent> eventStream() {
        return eventPublisher;
    }

    public CompletableFuture<AgentResult> executeEvents(String prompt, Flow.Subscriber<? super AgentEvent> subscriber) {
        eventPublisher.subscribe(subscriber);
        return executeAsync(prompt);
    }

    private AgentResult executeWithSession(String sid, String prompt, Map<String, Object> contextVariables,
                                           boolean useSessionManager) {
        if (useSessionManager && sessionManager != null) {
            var session = sessionManager.loadSession(sid)
                .orElseGet(() -> {
                    log.debug("Session {} not found, creating new one", sid);
                    return sessionManager.createSession("default", Map.of());
                });
            log.debug("Session {} loaded — {} messages", sid, session.messages().size());
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
                log.debug("Session {} saved — {} messages, status={}", sid, messages.size(), status);
            });
        }

        log.debug("executeLoop result — stopReason={}, durationMs={}, toolCalls={}, tokens={}/{}",
            result.stopReason(), result.metrics().durationMs(),
            result.metrics().toolCallsCount(),
            result.metrics().inputTokens(), result.metrics().outputTokens());

        return result;
    }

    private AgentResult executeLoop(String sid, String prompt, Map<String, Object> contextVariables) {
        log.debug("executeLoop start — sessionId={}", sid);
        var start = System.nanoTime();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int toolCallCount = 0;
        boolean structuredForceAttempted = false;
        boolean structuredForceActive = false;
        String structuredOutputResult = null;

        phase = AgentPhase.EXECUTING;
        fire(new AgentStartedEvent(sid, Instant.now(), prompt));

        var beforeAgentResult = hookRegistry.triggerBeforeAgent(
            new HookContexts.BeforeAgentContext(sid, prompt, contextVariables));
        if (beforeAgentResult instanceof HookResult.Modify<?> m) {
            log.debug("beforeAgent hook modified prompt — was '{}', now '{}'",
                truncate(prompt), truncate((String) m.value()));
            prompt = (String) m.value();
        } else if (beforeAgentResult instanceof HookResult.Cancel c) {
            log.debug("beforeAgent hook cancelled — reason={}", c.reason());
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            var result = new AgentResult(sid, "Hook cancelled: " + c.reason(),
                ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                new ExecutionMetrics(durationMs, 0, 0, 0),
                StopReason.INTERRUPTED);
            fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
            return result;
        }

        prompt = prompt.trim();
        chatMemory.add(UserMessage.from(prompt));

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            checkPaused();

            log.debug("Iteration {}/{} — chatMemory messages={}",
                iteration + 1, MAX_TOOL_ITERATIONS, chatMemory.messages().size());

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
                if (guardResult != null) {
                    log.debug("Input guardrail blocked — stopReason={}, answer={}",
                        guardResult.stopReason(), truncate(guardResult.finalAnswer()));
                    return guardResult;
                }
            }

            var sb = new StringBuilder(systemPrompt != null ? systemPrompt : "");
            var bie = new BeforeInvocationEvent(sid, Instant.now(), sb, domainMessages);
            fire(bie);
            for (var hook : pluginHooks) {
                hook.accept(sb);
            }
            systemPrompt = sb.toString().trim();

            var toolSpecs = structuredForceActive
                ? List.<dev.langchain4j.agent.tool.ToolSpecification>of()
                : toolRegistry.getSpecifications();

            // Hook: beforeModelCall
            var beforeMcCtx = new HookContexts.BeforeModelCallContext(
                sid, new StringBuilder(systemPrompt), domainMessages, toolSpecs);
            var beforeMcResult = hookRegistry.triggerBeforeModelCall(beforeMcCtx);
            systemPrompt = beforeMcCtx.systemPrompt().toString();
            if (beforeMcResult instanceof HookResult.Modify<?> m
                    && m.value() instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                var modifiedTools = (List<dev.langchain4j.agent.tool.ToolSpecification>) list;
                log.debug("beforeModelCall hook modified tools — {} → {} tools",
                    toolSpecs.size(), modifiedTools.size());
                toolSpecs = modifiedTools;
            }
            if (beforeMcResult instanceof HookResult.Cancel c) {
                log.debug("beforeModelCall hook cancelled — reason={}", c.reason());
                phase = AgentPhase.FAILED;
                var durationMs = (System.nanoTime() - start) / 1_000_000;
                var result = new AgentResult(sid, "Hook cancelled: " + c.reason(),
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.INTERRUPTED);
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }

            // LLM call with afterModelCall hook & retry support
            var responseText = "";
            AiMessage aiMessage = null;
            ChatResponse response = null;

            modelCall:
            for (int hookRetry = 0; hookRetry < MAX_HOOK_RETRIES; hookRetry++) {
                fire(new ModelRequestedEvent(sid, Instant.now(), domainMessages));

                try {
                    response = callWithResilience(currentMessages, toolSpecs);
                } catch (Exception e) {
                    phase = AgentPhase.FAILED;
                    var durationMs = (System.nanoTime() - start) / 1_000_000;
                    var result = new AgentResult(sid, "LLM-Fehler: " + e.getMessage(),
                        ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                        new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                        StopReason.ERROR);
                    fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                    return result;
                }

                aiMessage = response.aiMessage();
                responseText = aiMessage.text() != null ? aiMessage.text() : "";

                var inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
                var outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;

                totalInputTokens += inputTokens;
                totalOutputTokens += outputTokens;

                log.debug("LLM call — hookRetry={}, inputTokens={}, outputTokens={}, responseLen={}",
                    hookRetry, inputTokens, outputTokens, responseText.length());

                // Hook: afterModelCall
                var afterMc = hookRegistry.triggerAfterModelCall(
                    new HookContexts.AfterModelCallContext(sid, responseText, inputTokens, outputTokens), responseText);
                if (afterMc instanceof HookResult.Cancel c) {
                    log.debug("afterModelCall hook cancelled — reason={}", c.reason());
                    phase = AgentPhase.FAILED;
                    var durationMs = (System.nanoTime() - start) / 1_000_000;
                    var result = new AgentResult(sid, "Hook cancelled: " + c.reason(),
                        ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                        new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                        StopReason.INTERRUPTED);
                    fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                    return result;
                }
                if (afterMc instanceof HookResult.Modify<?> m) {
                    log.debug("afterModelCall hook modified response — was '{}', now '{}'",
                        truncate(responseText), truncate((String) m.value()));
                    responseText = (String) m.value();
                }
                if (afterMc instanceof HookResult.Retry) {
                    log.debug("afterModelCall hook requested retry — reason={}", ((HookResult.Retry) afterMc).reason());
                    continue;
                }
                break modelCall;
            }

            // Output guardrails
            fire(new AfterInvocationEvent(sid, Instant.now(), responseText, domainMessages));
            if (guardrailPlugin != null) {
                var guardResult = runOutputGuardrails(responseText, domainMessages);
                if (guardResult != null) {
                    log.debug("Output guardrail blocked — stopReason={}, answer={}",
                        guardResult.stopReason(), truncate(guardResult.finalAnswer()));
                    return guardResult;
                }
            }

            chatMemory.add(aiMessage);

            // Structured output: try to extract JSON if enabled
            if (structuredOutputConfig != null && structuredOutputConfig.isEnabled()
                && !aiMessage.hasToolExecutionRequests()) {
                try {
                    OBJECT_MAPPER.readTree(responseText);
                    structuredOutputResult = responseText;
                    log.debug("Structured output parsed successfully — {} chars", responseText.length());
                } catch (JsonProcessingException e) {
                    if (!structuredForceAttempted) {
                        log.debug("Structured output parse failed, forcing with prompt");
                        structuredForceAttempted = true;
                        structuredForceActive = true;
                        chatMemory.add(UserMessage.from(structuredOutputConfig.forcePrompt()));
                        continue;
                    }
                    log.debug("Structured output force attempt also failed");
                }
            }



            if (!aiMessage.hasToolExecutionRequests()) {
                // Hook: afterAgent
                var doneResult = new AgentResult(
                    sid,
                    responseText,
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics((System.nanoTime() - start) / 1_000_000, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.COMPLETED,
                    structuredOutputResult
                );
                var afterAgent = hookRegistry.triggerAfterAgent(
                    new HookContexts.AfterAgentContext(sid, doneResult), doneResult.finalAnswer());
                var finalAnswer = afterAgent instanceof HookResult.Modify<?> m
                    ? (String) m.value() : doneResult.finalAnswer();
                if (afterAgent instanceof HookResult.Modify<?> m) {
                    log.debug("afterAgent hook — answer was modified={} (len {}→{})",
                            afterAgent instanceof HookResult.Modify,
                            doneResult.finalAnswer().length(), finalAnswer.length());
                }

                phase = AgentPhase.COMPLETED;
                var result = new AgentResult(sid, finalAnswer, doneResult.generatedMessages(),
                    doneResult.metrics(), doneResult.stopReason(), doneResult.structuredOutput());
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }

            toolCallCount += aiMessage.toolExecutionRequests().size();

            // Execute tools one by one with before/after hooks
            for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                var args = parseArgs(req.arguments());

                // Hook: beforeToolCall
                var beforeTc = hookRegistry.triggerBeforeToolCall(
                    new HookContexts.BeforeToolCallContext(sid, req.name(), args));
                if (beforeTc instanceof HookResult.Cancel c) {
                    log.debug("beforeToolCall hook cancelled tool '{}' — reason={}", req.name(), c.reason());
                    fire(new ToolExecutionStartedEvent(sid, Instant.now(),
                        new ToolCall(req.id(), req.name(), req.arguments())));
                    fire(new ToolExecutionFinishedEvent(sid, Instant.now(),
                        new ToolExecutionResult(req.id(), req.name(), "Skipped: " + c.reason(), false)));
                    continue;
                }

                fire(new ToolExecutionStartedEvent(sid, Instant.now(),
                    new ToolCall(req.id(), req.name(), req.arguments())));

                try {
                    ToolExecutionResult toolResult;
                    if (contextVariables.isEmpty()) {
                        toolResult = wrapWithRetry(() ->
                            toolExecutor.execute(req, toolRegistry));
                    } else {
                        var prevSession = AgentContext.SESSION.get();
                        AgentContext.SESSION.set(contextVariables);
                        try {
                            toolResult = wrapWithRetry(() ->
                                toolExecutor.execute(req, toolRegistry));
                        } finally {
                            if (prevSession != null) {
                                AgentContext.SESSION.set(prevSession);
                            } else {
                                AgentContext.SESSION.remove();
                            }
                        }
                    }

                    // Hook: afterToolCall
                    var afterTcResult = hookRegistry.triggerAfterToolCall(
                        new HookContexts.AfterToolCallContext(sid, req.name(), toolResult.result(), toolResult.isError()),
                        toolResult.result());
                    var modifiedResult = afterTcResult instanceof HookResult.Modify<?>(Object value)
                            ? (String) value : toolResult.result();
                    if (afterTcResult instanceof HookResult.Modify<?>) {
                        log.debug("afterToolCall '{}' — isError={}, modified={}",
                                req.name(), toolResult.isError(), afterTcResult instanceof HookResult.Modify);
                    }
                    var finalToolResult = new ToolExecutionResult(req.id(), req.name(), modifiedResult, toolResult.isError());

                    var request = findRequest(aiMessage.toolExecutionRequests(), req.name());
                    if (request != null) {
                        chatMemory.add(ToolExecutionResultMessage.from(request, modifiedResult));
                    }
                    fire(new ToolExecutionFinishedEvent(sid, Instant.now(), finalToolResult));
                } catch (Exception e) {
                    Throwable cause = e;
                    while ((cause instanceof java.util.concurrent.ExecutionException ||
                            cause instanceof java.lang.reflect.InvocationTargetException) &&
                           cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    log.error("Tool execution error in '{}': {}", req.name(), cause.getMessage());
                    var errorMessage = retryConfig != null
                        ? "Tool '" + req.name() + "' failed after "
                            + retryConfig.maxAttempts() + " attempts: " + cause.getMessage()
                        : "Tool error: " + cause.getMessage();
                    var toolResult = new ToolExecutionResult(req.id(), req.name(), errorMessage, true);

                    var request = findRequest(aiMessage.toolExecutionRequests(), req.name());
                    if (request != null) {
                        log.debug("Tool execution error — request={}", truncate(String.valueOf(request)));
                        chatMemory.add(ToolExecutionResultMessage.from(request, errorMessage));
                    }
                    fire(new ToolExecutionFinishedEvent(sid, Instant.now(), toolResult));
                }
            }
        }

        log.debug("Max iterations ({}) reached — returning result", MAX_TOOL_ITERATIONS);
        phase = AgentPhase.FAILED;
        var durationMs = (System.nanoTime() - start) / 1_000_000;
        var result = new AgentResult(
            sid,
            "Maximale Iterationen erreicht",
            ChatMessageConverter.toDomainMessages(chatMemory.messages()),
            new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.MAX_ITERATIONS
        );
        var afterAgent = hookRegistry.triggerAfterAgent(
            new HookContexts.AfterAgentContext(sid, result), result.finalAnswer());
        var finalAnswer = afterAgent instanceof HookResult.Modify<?>(Object value)
            ? (String) value : result.finalAnswer();
        var modifiedResult = new AgentResult(sid, finalAnswer, result.generatedMessages(),
            result.metrics(), result.stopReason(), result.structuredOutput());
        fire(new AgentFinishedEvent(sid, Instant.now(), modifiedResult.finalAnswer()));
        return modifiedResult;
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
        log.debug("Guardrail blocked — action={}, reason='{}'", guardrailPlugin.blockAction(), guardResult.reason());
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
                if (checkpointService != null) {
                    var cp = checkpointService.createCheckpoint(sessionId, "guardrail", guardResult.reason());
                    phase = AgentPhase.WAITING_FOR_HUMAN;
                    try {
                        var resolved = checkpointService.await(cp);
                        if (resolved.status() == de.augmentia.strandsagents.core.plugin.hitl.checkpoint.Checkpoint.Status.APPROVED) {
                            phase = AgentPhase.EXECUTING;
                            yield null; // continue execution
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
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

    private Map<String, Object> parseArgs(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(arguments,
                new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public ChatMemoryStore getChatMemoryStore() {
        return chatMemoryStore;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public void addTool(Object toolInstance) {
        toolRegistry.register(toolInstance);
    }

    public void addTool(de.augmentia.strandsagents.core.tools.AgentTool<?> tool) {
        toolRegistry.register(tool);
    }

    public void removeTool(String name) {
        toolRegistry.remove(name);
    }

    public HookRegistry getHookRegistry() {
        return hookRegistry;
    }

    public void setHookRegistry(HookRegistry hookRegistry) {
        this.hookRegistry = hookRegistry != null ? hookRegistry : new HookRegistry();
    }

    public void addHook(de.augmentia.strandsagents.core.hook.AgentHook hook) {
        hookRegistry.register(hook);
    }

    public void removeHook(String name) {
        hookRegistry.unregister(name);
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public AgentPhase getPhase() {
        return phase;
    }

    public void setCheckpointService(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
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
