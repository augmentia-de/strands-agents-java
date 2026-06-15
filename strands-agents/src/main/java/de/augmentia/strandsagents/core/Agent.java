package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.LlmConfig;
import de.augmentia.strandsagents.config.ModelFactory;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.features.context.AgentContext;
import de.augmentia.strandsagents.features.conversation.ConversationManager;
import de.augmentia.strandsagents.features.sessions.SessionManager;
import de.augmentia.strandsagents.features.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.features.internal.ChatMessageConverter;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import de.augmentia.strandsagents.model.agent.*;
import de.augmentia.strandsagents.model.event.*;
import de.augmentia.strandsagents.model.session.Session;
import de.augmentia.strandsagents.model.tool.ToolCall;
import de.augmentia.strandsagents.model.tool.ToolExecutionResult;
import de.augmentia.strandsagents.features.pipeline.HookContexts;
import de.augmentia.strandsagents.features.pipeline.HookRegistry;
import de.augmentia.strandsagents.features.pipeline.HookResult;
import de.augmentia.strandsagents.features.plugin.Plugin;
import de.augmentia.strandsagents.features.plugin.PluginRegistry;
import de.augmentia.strandsagents.features.guardrails.GuardrailException;
import de.augmentia.strandsagents.features.guardrails.GuardrailResult;
import de.augmentia.strandsagents.features.hitl.checkpoint.Checkpoint;
import de.augmentia.strandsagents.features.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.features.resilience.*;
import de.augmentia.strandsagents.features.structured.StructuredOutputConfig;
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
    private ChatModel advancedModel;
    private ModelTier currentTier;
    private final ChatMemory chatMemory;
    private ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private volatile List<String> lastToolNames = List.of();
    private String sessionId;
    private ChatModel simpleModel;
    private final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    private final RetryConfig retryConfig;
    private final CircuitBreaker circuitBreaker;
    private final SubmissionPublisher<AgentEvent> eventPublisher;
    private final List<AgentEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private String systemPrompt = "";
    private List<Plugin> plugins = new ArrayList<>();
    private CheckpointService checkpointService;
    private volatile AgentPhase phase = AgentPhase.IDLE;
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition pauseCondition = pauseLock.newCondition();
    private StructuredOutputConfig structuredOutputConfig;
    private HookRegistry hookRegistry;
    private final ChatMemoryStore chatMemoryStore;
    private volatile String lastThinking;
    private volatile boolean cancelled = false;
    private volatile Thread executionThread;

    /**
     * Mutable runtime configuration snapshot source.
     * <p>
     * During construction, {@link #initRunConfig()} copies the Agent's own fields
     * ({@link #toolRegistry}, {@link #hookRegistry}, {@link #systemPrompt},
     * {@link #structuredOutputConfig}) into this config object. All subsequent
     * setters on Agent (e.g. {@link #setSystemPrompt(String)}) update both the
     * Agent field and this config in parallel, while getters prefer the config
     * value with the Agent field as fallback.
     * <p>
     * At the start of {@link #executeLoop(String, String, Map)} a call to
     * {@link AgentRunConfig#snapshot()} captures an immutable {@link RunSnapshot}
     * that is used for the entire execution. This ensures consistent configuration
     * even if the Agent's fields are mutated concurrently by a different thread
     * (e.g. via {@link #setToolRegistry(ToolRegistry)}).
     */
    private final AgentRunConfig runConfig = new AgentRunConfig();

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
        initRunConfig();
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
        runConfig.setHookRegistry(this.hookRegistry);
    }

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ResilienceConfig resilienceConfig, List<Plugin> plugins) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig);
        initPlugins(plugins);
    }

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
                 List<Plugin> plugins) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager,
            chatMemoryStore, resilienceConfig);
        initPlugins(plugins);
    }

    public Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor,
                 ConversationManager conversationManager, SessionManager sessionManager,
                 ResilienceConfig resilienceConfig, List<Plugin> plugins, HookRegistry hookRegistry) {
        this(model, toolRegistry, toolExecutor, conversationManager, sessionManager, resilienceConfig, hookRegistry);
        initPlugins(plugins);
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

    /**
     * Copies the Agent's current field values into {@link #runConfig}.
     * Called once at the end of the main constructor so that the mutable
     * config reflects the initial component setup. Subsequent mutations go
     * through the individual setters which update both sides in parallel.
     */
    private void initRunConfig() {
        runConfig.setToolRegistry(toolRegistry);
        runConfig.setHookRegistry(hookRegistry);
        runConfig.setSystemPrompt(systemPrompt);
        runConfig.setStructuredOutputConfig(structuredOutputConfig);
    }

    /**
     * Returns the mutable runtime configuration holder.
     * <p>
     * The returned {@link AgentRunConfig} can be used to inspect or modify
     * the agent's current configuration outside of the main Agent API.
     * Call {@link AgentRunConfig#snapshot()} to capture an immutable view
     * for safe use across thread boundaries.
     *
     * @return the runtime config (never null)
     */
    public AgentRunConfig getRunConfig() {
        return runConfig;
    }

    public List<Plugin> getPlugins() {
        return plugins;
    }

    public List<Plugin> getOrderedPlugins() {
        var sorted = new ArrayList<>(plugins);
        sorted.sort(java.util.Comparator.comparingInt(Plugin::order));
        return List.copyOf(sorted);
    }

    private void initPlugins(List<Plugin> plugins) {
        if (plugins != null && !plugins.isEmpty()) {
            this.plugins = new ArrayList<>(plugins);
            var registry = new PluginRegistry(plugins);
            registry.initialize(this);
        }
    }

    public String getLastThinking() {
        return lastThinking;
    }

    public void setEventListener(AgentEventListener eventListener) {
        this.eventListeners.clear();
        if (eventListener != null) {
            this.eventListeners.add(eventListener);
        }
    }

    public void addEventListener(AgentEventListener eventListener) {
        if (eventListener != null) {
            this.eventListeners.add(eventListener);
        }
    }

    public void removeEventListener(AgentEventListener eventListener) {
        this.eventListeners.remove(eventListener);
    }

    public void setStructuredOutputModel(Class<?> modelClass) {
        this.structuredOutputConfig = StructuredOutputConfig.staticModel(modelClass);
    }

    public void setStructuredOutputSchema(String jsonSchema) {
        this.structuredOutputConfig = StructuredOutputConfig.dynamicSchema(jsonSchema);
    }

    public void setStructuredOutputConfig(StructuredOutputConfig config) {
        this.structuredOutputConfig = config;
        runConfig.setStructuredOutputConfig(config);
    }

    public StructuredOutputConfig getStructuredOutputConfig() {
        return runConfig.getStructuredOutputConfig() != null ? runConfig.getStructuredOutputConfig() : structuredOutputConfig;
    }

    public String getSystemPrompt() {
        return runConfig.getSystemPrompt();
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        runConfig.setSystemPrompt(systemPrompt);
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

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    private AgentResult executeWithSession(String sid, String prompt, Map<String, Object> contextVariables,
                                           boolean useSessionManager) {
        this.sessionId = sid;
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
        var run = runConfig.snapshot();
        cancelled = false;
        executionThread = Thread.currentThread();
        try {
        var start = System.nanoTime();
        int totalInputTokens = 0;
        int totalOutputTokens = 0;
        int toolCallCount = 0;
        boolean structuredForceAttempted = false;
        boolean structuredForceActive = false;
        String structuredOutputResult = null;

        phase = AgentPhase.EXECUTING;
        fire(new AgentStartedEvent(sid, Instant.now(), prompt));

        var beforeAgentResult = run.hookRegistry().triggerBeforeAgent(
            new HookContexts.BeforeAgentContext(sid, prompt, contextVariables));
        if (beforeAgentResult instanceof HookResult.Modify<?> m) {
            log.debug("beforeAgent hook modified prompt — was '{}', now '{}'",
                truncate(prompt), truncate((String) m.value()));
            prompt = (String) m.value();
        } else if (beforeAgentResult instanceof HookResult.Cancel c) {
            log.debug("beforeAgent hook cancelled — reason={}", c.reason());
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            var result = new AgentResult(sid, PromptRegistry.get("agent.hook_cancelled", c.reason()),
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
            if (cancelled) throw new RuntimeException("Agent execution cancelled by user");

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

            // Input guardrails (iterate all plugins)
            var orderedPlugins = getOrderedPlugins();
            for (var plugin : orderedPlugins) {
                for (var g : plugin.getInputGuardrails()) {
                    var result = g.validate(domainMessages, plugin.name());
                    if (!result.pass()) {
                        var guardResult = handlePluginGuardrail(sid, start, plugin, result,
                            totalInputTokens, totalOutputTokens, toolCallCount);
                        if (guardResult != null) {
                            log.debug("Input guardrail blocked — stopReason={}, answer={}",
                                guardResult.stopReason(), truncate(guardResult.finalAnswer()));
                            return guardResult;
                        }
                        // null means ESCALATE approved — continue
                    }
                }
            }

            var sb = new StringBuilder(run.systemPrompt() != null ? run.systemPrompt() : "");
            var bie = new BeforeInvocationEvent(sid, Instant.now(), sb, domainMessages);
            fire(bie);
            var effectivePrompt = sb.toString().trim();

            List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecs = structuredForceActive
                ? java.util.Collections.emptyList()
                : run.toolRegistry() != null ? run.toolRegistry().getSpecifications()
                  : java.util.Collections.emptyList();

            // Hook: beforeModelCall
            var beforeMcCtx = new HookContexts.BeforeModelCallContext(
                sid, new StringBuilder(effectivePrompt), domainMessages, toolSpecs, new ArrayList<>());
            var beforeMcResult = run.hookRegistry().triggerBeforeModelCall(beforeMcCtx);
            effectivePrompt = beforeMcCtx.systemPrompt().toString();
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
                var result = new AgentResult(sid, PromptRegistry.get("agent.hook_cancelled", c.reason()),
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.INTERRUPTED);
                fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                return result;
            }

            // Detect tool changes and notify the LLM
            var currentToolNames = toolSpecs.stream()
                .map(ToolSpecification::name)
                .sorted()
                .toList();
            if (!currentToolNames.equals(lastToolNames)) {
                if (!lastToolNames.isEmpty()) {
                    var added = new ArrayList<>(currentToolNames);
                    added.removeAll(lastToolNames);
                    var removed = new ArrayList<>(lastToolNames);
                    removed.removeAll(currentToolNames);
                    var notice = new StringBuilder("SYSTEM NOTE: Your available tools have been updated.");
                    if (!added.isEmpty()) {
                        notice.append(" Added: ").append(String.join(", ", added));
                    }
                    if (!removed.isEmpty()) {
                        notice.append(" Removed: ").append(String.join(", ", removed));
                    }
                    log.debug("Tool change detected — added={}, removed={}", added, removed);
                    beforeMcCtx.additionalMessages().add(
                        new de.augmentia.strandsagents.model.message.SystemMessage(
                            UUID.randomUUID().toString(), Instant.now(), notice.toString(), Map.of()));
                }
                lastToolNames = currentToolNames;
            }

            // Persist additional messages from hooks and tool change notifications into chatMemory
            if (!beforeMcCtx.additionalMessages().isEmpty()) {
                for (var msg : beforeMcCtx.additionalMessages()) {
                    chatMemory.add(ChatMessageConverter.toLangChain4j(msg));
                }
                currentMessages = chatMemory.messages();
                domainMessages = ChatMessageConverter.toDomainMessages(currentMessages);
            }

            // LLM call with afterModelCall hook & retry support
            var responseText = "";
            AiMessage aiMessage = null;
            ChatResponse response = null;

            modelCall:
            for (int hookRetry = 0; hookRetry < MAX_HOOK_RETRIES; hookRetry++) {
                fire(new ModelRequestedEvent(sid, Instant.now(), domainMessages));

                try {
                    response = callWithResilience(currentMessages, toolSpecs, effectivePrompt);
                } catch (Exception e) {
                    phase = AgentPhase.FAILED;
                    var durationMs = (System.nanoTime() - start) / 1_000_000;
                    var result = new AgentResult(sid, PromptRegistry.get("agent.llm_error", e.getMessage()),
                        ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                        new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
                        StopReason.ERROR);
                    fire(new AgentFinishedEvent(sid, Instant.now(), result.finalAnswer()));
                    return result;
                }

                aiMessage = response.aiMessage();
                responseText = aiMessage.text() != null ? aiMessage.text() : "";
                lastThinking = aiMessage.thinking();

                var inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
                var outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;

                totalInputTokens += inputTokens;
                totalOutputTokens += outputTokens;

                log.debug("LLM call — hookRetry={}, inputTokens={}, outputTokens={}, responseLen={}",
                    hookRetry, inputTokens, outputTokens, responseText.length());

                // Hook: afterModelCall
                var afterMc = run.hookRegistry().triggerAfterModelCall(
                    new HookContexts.AfterModelCallContext(sid, responseText, inputTokens, outputTokens), responseText);
                if (afterMc instanceof HookResult.Cancel c) {
                    log.debug("afterModelCall hook cancelled — reason={}", c.reason());
                    phase = AgentPhase.FAILED;
                    var durationMs = (System.nanoTime() - start) / 1_000_000;
                    var result = new AgentResult(sid, PromptRegistry.get("agent.hook_cancelled", c.reason()),
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

            // Output guardrails (iterate all plugins)
            fire(new AfterInvocationEvent(sid, Instant.now(), responseText, domainMessages));
            for (var plugin : getOrderedPlugins()) {
                for (var g : plugin.getOutputGuardrails()) {
                    var result = g.validate(domainMessages, "output:" + responseText);
                    if (!result.pass()) {
                        var guardResult = handlePluginGuardrail(sid, start, plugin, result,
                            totalInputTokens, totalOutputTokens, toolCallCount);
                        if (guardResult != null) {
                            log.debug("Output guardrail blocked — stopReason={}, answer={}",
                                guardResult.stopReason(), truncate(guardResult.finalAnswer()));
                            return guardResult;
                        }
                    }
                }
            }

            chatMemory.add(aiMessage);

            // Structured output: try to extract JSON if enabled
            var soConfig = run.structuredOutputConfig();
            if (soConfig != null && soConfig.isEnabled()
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
                        chatMemory.add(UserMessage.from(soConfig.forcePrompt()));
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
                var afterAgent = run.hookRegistry().triggerAfterAgent(
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
                var beforeTc = run.hookRegistry().triggerBeforeToolCall(
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
                            toolExecutor.execute(req, run.toolRegistry()));
                    } else {
                        var prevSession = AgentContext.SESSION.get();
                        AgentContext.SESSION.set(contextVariables);
                        try {
                            toolResult = wrapWithRetry(() ->
                                toolExecutor.execute(req, run.toolRegistry()));
                        } finally {
                            if (prevSession != null) {
                                AgentContext.SESSION.set(prevSession);
                            } else {
                                AgentContext.SESSION.remove();
                            }
                        }
                    }

                    // Hook: afterToolCall
                    var afterTcResult = run.hookRegistry().triggerAfterToolCall(
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
            PromptRegistry.getOrDefault("agent.max_iterations", "Maximum iterations reached"),
            ChatMessageConverter.toDomainMessages(chatMemory.messages()),
            new ExecutionMetrics(durationMs, totalInputTokens, totalOutputTokens, toolCallCount),
            StopReason.MAX_ITERATIONS
        );
        var afterAgent = run.hookRegistry().triggerAfterAgent(
            new HookContexts.AfterAgentContext(sid, result), result.finalAnswer());
        var finalAnswer = afterAgent instanceof HookResult.Modify<?>(Object value)
            ? (String) value : result.finalAnswer();
        var modifiedResult = new AgentResult(sid, finalAnswer, result.generatedMessages(),
            result.metrics(), result.stopReason(), result.structuredOutput());
        fire(new AgentFinishedEvent(sid, Instant.now(), modifiedResult.finalAnswer()));
        return modifiedResult;
        } finally {
            executionThread = null;
        }
    }

    private AgentResult handlePluginGuardrail(String sid, long startNanos, Plugin plugin,
                                               GuardrailResult guardResult,
                                               int totalInputTokens, int totalOutputTokens, int toolCallCount) {
        log.debug("Guardrail blocked by '{}' — action={}, reason='{}'",
            plugin.name(), plugin.getBlockAction(), guardResult.reason());
        var sanitized = guardResult.sanitized();
        return switch (plugin.getBlockAction()) {
            case THROW -> {
                var msg = sanitized != null
                    ? guardResult.reason() + " (sanitized: " + sanitized + ")"
                    : guardResult.reason();
                throw new GuardrailException(msg);
            }
            case FALLBACK -> {
                phase = AgentPhase.FAILED;
                var answer = sanitized != null ? sanitized : plugin.getFallbackMessage();
                fire(new AgentFinishedEvent(sid, Instant.now(), answer));
                yield new AgentResult(
                    sid,
                    answer,
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics((System.nanoTime() - startNanos) / 1_000_000,
                        totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.ERROR
                );
            }
            case ESCALATE -> {
                if (checkpointService != null) {
                    var cp = checkpointService.createCheckpoint(sid, "guardrail:" + plugin.name(), guardResult.reason());
                    phase = AgentPhase.WAITING_FOR_HUMAN;
                    try {
                        var resolved = checkpointService.await(cp);
                        if (resolved.status() == Checkpoint.Status.APPROVED) {
                            phase = AgentPhase.EXECUTING;
                            yield null; // continue execution
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                phase = AgentPhase.FAILED;
                var answer = sanitized != null ? sanitized : plugin.getFallbackMessage();
                fire(new AgentFinishedEvent(sid, Instant.now(), answer));
                yield new AgentResult(
                    sid,
                    answer,
                    ChatMessageConverter.toDomainMessages(chatMemory.messages()),
                    new ExecutionMetrics((System.nanoTime() - startNanos) / 1_000_000,
                        totalInputTokens, totalOutputTokens, toolCallCount),
                    StopReason.ERROR
                );
            }
        };
    }

    protected ChatResponse doChat(ChatRequest request) {
        return model.chat(request);
    }

    private ChatResponse callWithResilience(List<ChatMessage> currentMessages, List<ToolSpecification> toolSpecs, String effectivePrompt) {
        var recovery = new TokenRecovery();
        var msgs = currentMessages;

        while (true) {
            try {
                var request = buildRequest(msgs, toolSpecs, effectivePrompt);

                Callable<ChatResponse> chatCall = () -> doChat(request);

                if (circuitBreaker != null) {
                    RetryConfig cfg = effectiveRetryConfig();
                    return circuitBreaker.call(
                        () -> Retry.run(chatCall, cfg),
                        () -> { throw new RuntimeException("CircuitBreaker: Service temporarily unavailable"); });
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

    private ChatRequest buildRequest(List<ChatMessage> messages, List<ToolSpecification> toolSpecs, String effectivePrompt) {
        var builder = ChatRequest.builder();
        if (effectivePrompt != null && !effectivePrompt.isBlank()) {
            var allMessages = new ArrayList<ChatMessage>();
            allMessages.add(SystemMessage.from(effectivePrompt));
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
        for (var listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.warn("Error in event listener: {}", e.getMessage());
            }
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
        return runConfig.getToolRegistry() != null ? runConfig.getToolRegistry() : toolRegistry;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        runConfig.setToolRegistry(toolRegistry);
    }

    public void addTool(Object toolInstance) {
        var reg = runConfig.getToolRegistry() != null ? runConfig.getToolRegistry() : toolRegistry;
        reg.register(toolInstance);
    }

    public void addTool(de.augmentia.strandsagents.features.tools.AgentTool<?> tool) {
        var reg = runConfig.getToolRegistry() != null ? runConfig.getToolRegistry() : toolRegistry;
        reg.register(tool);
    }

    public void removeTool(String name) {
        var reg = runConfig.getToolRegistry() != null ? runConfig.getToolRegistry() : toolRegistry;
        reg.remove(name);
    }

    public HookRegistry getHookRegistry() {
        return runConfig.getHookRegistry() != null ? runConfig.getHookRegistry() : hookRegistry;
    }

    public void setHookRegistry(HookRegistry hookRegistry) {
        this.hookRegistry = hookRegistry != null ? hookRegistry : new HookRegistry();
        runConfig.setHookRegistry(this.hookRegistry);
    }

    public void addHook(de.augmentia.strandsagents.features.pipeline.AgentHook hook) {
        var reg = runConfig.getHookRegistry() != null ? runConfig.getHookRegistry() : hookRegistry;
        reg.register(hook);
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

    public void cancel() {
        cancelled = true;
        reject("Cancelled by user");
        var t = executionThread;
        if (t != null) {
            t.interrupt();
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

    public ChatModel getCurrentModel() {
        if (currentTier == ModelTier.ADVANCED && advancedModel != null) {
            return advancedModel;
        }
        return model;
    }

    public void setAdvancedModel(ChatModel advancedModel) {
        this.advancedModel = advancedModel;
    }

    public void setSimpleModel(ChatModel simpleModel) {
        this.simpleModel = simpleModel;
    }

    public void setModelTier(ModelTier tier) {
        this.currentTier = tier;
    }

    public ModelTier getModelTier() {
        return currentTier;
    }

    public void switchTier(ModelTier tier) {
        this.currentTier = tier;
        if (tier == ModelTier.ADVANCED && advancedModel != null) {
            log.debug("Switched to ADVANCED model tier");
        } else {
            log.debug("Switched to SIMPLE model tier");
        }
    }
}
