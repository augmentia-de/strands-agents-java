package de.augmentia.strandsagents.core;

import de.augmentia.strandsagents.config.AgentSettings;
import de.augmentia.strandsagents.config.ModelTier;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.sessions.SessionManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.interceptor.pipeline.AgentHook;
import de.augmentia.strandsagents.interceptor.pipeline.HookRegistry;
import de.augmentia.strandsagents.interceptor.resilience.CircuitBreaker;
import de.augmentia.strandsagents.interceptor.resilience.ResilienceConfig;
import de.augmentia.strandsagents.interceptor.resilience.RetryConfig;

import de.augmentia.strandsagents.model.agent.*;
import de.augmentia.strandsagents.model.event.*;
import de.augmentia.strandsagents.model.session.Session;

import de.augmentia.strandsagents.interceptor.plugin.Plugin;
import de.augmentia.strandsagents.interceptor.plugin.PluginRegistry;
import de.augmentia.strandsagents.interceptor.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.model.structured.StructuredOutputConfig;
import de.augmentia.strandsagents.interceptor.telemetry.FileLlmLogger;
import de.augmentia.strandsagents.model.message.Message;
import de.augmentia.strandsagents.tools.AgentTool;
import dev.langchain4j.memory.ChatMemory;


import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
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
public class Agent implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final int LOG_MAX = 2000;

    private static String truncate(String s) {
        if (s == null) return "null";
        return s.length() <= LOG_MAX ? s : s.substring(0, LOG_MAX) + "...";
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static final int MAX_HOOK_RETRIES = 3;
    static final int DEFAULT_MAX_MESSAGES = 75;
    static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    private final ChatModel model;
    private ChatModel advancedModel;
    private ModelTier currentTier;
    final ChatMemory chatMemory;
    private ToolRegistry toolRegistry;
    final ToolExecutor toolExecutor;
    volatile List<String> lastToolNames = List.of();
    private String sessionId;
    private ChatModel simpleModel;
    final ConversationManager conversationManager;
    private final SessionManager sessionManager;
    final RetryConfig retryConfig;
    final CircuitBreaker circuitBreaker;
    final Duration modelTimeout;
    private final SubmissionPublisher<AgentEvent> eventPublisher;
    private final List<AgentEventListener> eventListeners = new CopyOnWriteArrayList<>();
    private String systemPrompt = "";
    private int maxToolIterations = AgentSettings.DEFAULT_MAX_TOOL_ITERATIONS;
    private List<Plugin> plugins = new ArrayList<>();
    CheckpointService checkpointService;
    volatile String lastThinking;
    volatile boolean cancelled = false;
    volatile Thread executionThread;
    AtomicBoolean abortFlag = new AtomicBoolean(false);
    private FileLlmLogger llmLogger;
    private final List<AutoCloseable> resources = new ArrayList<>();
    private volatile boolean shutdown = false;
    ChatMemoryStore chatMemoryStore;
    HookRegistry hookRegistry;
    StructuredOutputConfig structuredOutputConfig;
    AgentPhase phase = AgentPhase.IDLE;
    ReentrantLock pauseLock = new ReentrantLock();
    Condition pauseCondition = pauseLock.newCondition();

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
    final AgentRunConfig runConfig = new AgentRunConfig();

    /**
     * Constructs an Agent with only a chat model and default components.
     *
     * @param model the chat model to use for orchestration
     */
    public Agent(ChatModel model) {
        this(model, new ToolRegistry(), new DefaultToolExecutor(), null, null);
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
        this.modelTimeout = resilienceConfig != null ? resilienceConfig.modelTimeout() : null;
        this.chatMemoryStore = chatMemoryStore;
        int maxMessages = conversationManager instanceof SlidingWindowConversationManager sw
            ? sw.windowSize() : DEFAULT_MAX_MESSAGES;
        this.chatMemory = new MultiSystemMessageChatMemory(maxMessages);
        if (chatMemoryStore != null) {
            log.warn("ChatMemoryStore {} ignored — use SessionManager for persistence",
                chatMemoryStore.getClass().getSimpleName());
        }
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

    /*
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
        runConfig.setMaxToolIterations(maxToolIterations);
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
        setStructuredOutputConfig(StructuredOutputConfig.staticModel(modelClass));
    }

    public void setStructuredOutputSchema(String jsonSchema) {
        setStructuredOutputConfig(StructuredOutputConfig.dynamicSchema(jsonSchema));
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

    public int getMaxToolIterations() {
        return runConfig.getMaxToolIterations();
    }

    public void setMaxToolIterations(int maxToolIterations) {
        this.maxToolIterations = maxToolIterations;
        runConfig.setMaxToolIterations(maxToolIterations);
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
                    var now = Instant.now();
                    var state = new AgentState(sid, List.of(), Map.of(), AgentStatus.IDLE);
                    var newSession = new Session(sid, "default", List.of(), state, Map.of(), now, now);
                    sessionManager.saveSession(newSession);
                    return newSession;
                });
            log.debug("Session {} loaded — {} messages", sid, session.messages().size());
            chatMemory.clear();
            session.messages().stream().map(Message::toChatMessage)
                .forEach(chatMemory::add);
        }

        var result = executeLoop(sid, prompt, contextVariables);

        if (useSessionManager && sessionManager != null) {
            var messages = chatMemory.messages().stream().map(Message::from).toList();
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
        return new AgentLoop(this, sid, prompt, contextVariables).execute();
    }



    /**
     * Delegates a chat request to the underlying model. Override to intercept or stream.
     */
    protected ChatResponse doChat(ChatRequest request) {
        return model.chat(request);
    }

    /**
     * Dispatches an event to all registered listeners and the event publisher.
     */
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

    public void addTool(AgentTool<?> tool) {
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

    public void addHook(AgentHook hook) {
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

    /**
     * Pauses execution and transitions to WAITING_FOR_HUMAN phase for HITL approval.
     */
    public void pauseExecution() {
        phase = AgentPhase.WAITING_FOR_HUMAN;
    }

    /**
     * Resumes a paused execution by signaling the pause condition.
     */
    public void resumeExecution() {
        pauseLock.lock();
        try {
            phase = AgentPhase.EXECUTING;
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Convenience method — resumes execution (alias for {@link #resumeExecution()}).
     */
    public void approve() {
        resumeExecution();
    }

    /**
     * Rejects execution, transitioning to FAILED phase and signaling the pause condition.
     */
    public void reject(String reason) {
        pauseLock.lock();
        try {
            phase = AgentPhase.FAILED;
            pauseCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    /**
     * Cancels the current execution, interrupts the execution thread, and sets the abort flag.
     */
    public void cancel() {
        cancelled = true;
        abortFlag.set(true);
        reject("Cancelled by user");
        var t = executionThread;
        if (t != null) {
            t.interrupt();
        }
        if (toolRegistry != null) {
            toolRegistry.setAbortFlag(abortFlag);
        }
    }

    public AtomicBoolean getAbortFlag() {
        return abortFlag;
    }

    public void setLlmLogger(FileLlmLogger logger) {
        this.llmLogger = logger;
        if (logger != null) {
            resources.add(logger);
        }
    }

    /**
     * Shuts down the agent: cancels execution and closes all registered resources.
     */
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;
        cancel();
        for (var resource : resources) {
            try {
                resource.close();
            } catch (Exception e) {
                log.debug("Error closing resource: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Returns the model corresponding to the current tier (ADVANCED or default).
     */
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

    /**
     * Switches the active model tier and logs the change.
     */
    public void switchTier(ModelTier tier) {
        this.currentTier = tier;
        if (tier == ModelTier.ADVANCED && advancedModel != null) {
            log.debug("Switched to ADVANCED model tier");
        } else {
            log.debug("Switched to SIMPLE model tier");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing an {@link Agent} with optional components.
     */
    public static class Builder {
        private ChatModel model;
        private ToolRegistry toolRegistry = new ToolRegistry();
        private ToolExecutor toolExecutor = new DefaultToolExecutor();
        private ConversationManager conversationManager;
        private SessionManager sessionManager;
        ChatMemoryStore chatMemoryStore;
        private ResilienceConfig resilienceConfig;
        HookRegistry hookRegistry;
        private List<Plugin> plugins;
        private String systemPrompt;
        StructuredOutputConfig structuredOutputConfig;
        private CheckpointService checkpointService;
        private AgentEventListener eventListener;

        Builder() {}

        public Builder model(ChatModel model) {
            this.model = model;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder conversationManager(ConversationManager conversationManager) {
            this.conversationManager = conversationManager;
            return this;
        }

        public Builder sessionManager(SessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public Builder chatMemoryStore(ChatMemoryStore chatMemoryStore) {
            this.chatMemoryStore = chatMemoryStore;
            return this;
        }

        public Builder resilienceConfig(ResilienceConfig resilienceConfig) {
            this.resilienceConfig = resilienceConfig;
            return this;
        }

        public Builder hookRegistry(HookRegistry hookRegistry) {
            this.hookRegistry = hookRegistry;
            return this;
        }

        public Builder plugins(List<Plugin> plugins) {
            this.plugins = plugins;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder structuredOutputConfig(StructuredOutputConfig structuredOutputConfig) {
            this.structuredOutputConfig = structuredOutputConfig;
            return this;
        }

        public Builder checkpointService(CheckpointService checkpointService) {
            this.checkpointService = checkpointService;
            return this;
        }

        public Builder eventListener(AgentEventListener eventListener) {
            this.eventListener = eventListener;
            return this;
        }

        /**
         * Builds the agent, throwing if no model has been set.
         */
        public Agent build() {
            if (model == null) {
                throw new IllegalStateException("Agent model is required");
            }
            var agent = new Agent(model, toolRegistry, toolExecutor,
                conversationManager, sessionManager,
                chatMemoryStore, resilienceConfig);
            if (hookRegistry != null) {
                agent.hookRegistry = hookRegistry;
                agent.runConfig.setHookRegistry(hookRegistry);
            }
            if (plugins != null && !plugins.isEmpty()) {
                agent.initPlugins(plugins);
            }
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                agent.setSystemPrompt(systemPrompt);
            }
            if (structuredOutputConfig != null) {
                agent.setStructuredOutputConfig(structuredOutputConfig);
            }
            if (checkpointService != null) {
                agent.checkpointService = checkpointService;
            }
            if (eventListener != null) {
                agent.setEventListener(eventListener);
            }
            return agent;
        }
    }
}
