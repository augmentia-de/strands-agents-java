package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.MockStreamingChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.AgentFactory;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.SummarizingConversationManager;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointHook;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.skills.*;
import dev.langchain4j.mcp.client.McpClient;
import de.augmentia.strandsagents.core.config.StrandsAgentConfig;
import de.augmentia.strandsagents.core.mcp.McpConnector;
import de.augmentia.strandsagents.core.model.api.AgentInitRequest;
import de.augmentia.strandsagents.core.model.api.ChatRequest;
import de.augmentia.strandsagents.core.model.api.ChatResponse;
import de.augmentia.strandsagents.core.model.api.McpServerSelection;
import de.augmentia.strandsagents.core.model.api.SkillInfo;
import de.augmentia.strandsagents.core.model.api.ToolInfo;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.local.BashTool;
import de.augmentia.strandsagents.core.tools.HumanInTheLoopTool;
import de.augmentia.strandsagents.core.tools.local.ReadTool;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class AgentService implements de.augmentia.strandsagents.core.service.AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String TOOLS_PLACEHOLDER = "{{tools}}";
    private static final String SKILLS_PLACEHOLDER = "{{skills}}";
    static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful general agent. "
        + "Use the selected tools and skills when they are relevant, and explain important results clearly.";

    @Inject
    SecretService secretService;

    private StrandsAgentConfig config;
    private ToolRegistry fullRegistry;
    private List<Skill> allSkills;
    private ChatModel model;
    private SessionManager sessionManager;
    private Path logDir;
    private CapabilityRegistry capabilityRegistry;
    private CheckpointService checkpointService;
    private SSEChannel sseChannel;

    private final ConcurrentHashMap<String, InitializedSession> initializedAgents = new ConcurrentHashMap<>();

    private record InitializedSession(
        Agent agent,
        ToolRegistry registry,
        List<McpClient> mcpClients,
        List<String> phases,
        StreamingChatModel streamingModel,
        String systemPrompt
    ) {
        void close() {
            if (mcpClients != null) {
                for (var c : mcpClients) {
                    try { c.close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    static class ToolCallCapture {
        final String toolName;
        final String arguments;
        final long startedAt;
        String result;
        boolean isError;

        ToolCallCapture(String toolName, String arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
            this.startedAt = System.nanoTime();
        }

        long durationMs() { return (System.nanoTime() - startedAt) / 1_000_000; }

        ChatResponse.ToolCallInfo toInfo(ObjectMapper om) {
            var info = new ChatResponse.ToolCallInfo();
            info.name = toolName;
            info.durationMs = durationMs();
            info.success = !isError;
            info.result = result != null ? result.length() > 500 ? result.substring(0, 500) + "..." : result : "";
            try {
                info.arguments = om.readValue(arguments, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                info.arguments = Map.of("raw", arguments);
            }
            return info;
        }
    }

    private static final ObjectMapper TOOL_CALL_MAPPER = new ObjectMapper()
        .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    void init() {
        this.config = StrandsAgentConfig.fromMixed();
        this.logDir = Path.of(config.llmLogPath()).getParent();
        this.capabilityRegistry = buildCapabilityRegistry();
    }

    @PreDestroy
    void cleanup() {
        for (var session : initializedAgents.values()) {
            session.close();
        }
        initializedAgents.clear();
    }

    public synchronized void ensureInitialized() {
        if (model != null) return;

        this.model = createModel();
        this.fullRegistry = AgentFactory.createToolRegistry(config);
        this.allSkills = loadSkills();
        this.sessionManager = AgentFactory.createSessionManager(Path.of(config.sessionDir()));

        this.sseChannel = new SSEChannel();
        this.checkpointService = AgentFactory.createCheckpointService(config, sseChannel);

        if (config.mcpIngestEnabled()) {
            fullRegistry.register(new McpIngestTool(fullRegistry));
        }
        if (capabilityRegistry != null) {
            fullRegistry.register(ToolRegistry.createMethod(
                new CapabilitySearchTool(capabilityRegistry, model)));
        }

        setupLogging();
    }

    public ChatResponse initAgent(AgentInitRequest req) {
        return initAgent(req, UUID.randomUUID().toString());
    }

    public ChatResponse reinitAgent(AgentInitRequest req) {
        if (req.sessionId == null || req.sessionId.isBlank()) {
            var err = new ChatResponse();
            err.error = "sessionId erforderlich für Reinitialisierung";
            return err;
        }
        releaseSession(req.sessionId);
        return initAgent(req, req.sessionId);
    }

    private ChatResponse initAgent(AgentInitRequest req, String sessionId) {
        ensureInitialized();
        var start = System.nanoTime();

        var selectedTools = req.tools != null && !req.tools.isEmpty()
            ? fullRegistry.withOnly(new HashSet<>(req.tools))
            : fullRegistry.withOnly(new HashSet<>());

        // Mode 2: add MCP ingest tool per-session
        boolean effectiveMcpIngest = req.mcpIngestEnabled != null ? req.mcpIngestEnabled : config.mcpIngestEnabled();
        if (effectiveMcpIngest && !config.mcpIngestEnabled()) {
            selectedTools.register(new McpIngestTool(selectedTools));
        }

        // Mode 3: add capability search tool per-session
        var effectiveCapDirs = req.capabilityDirs != null ? req.capabilityDirs
            : System.getProperty("strands.agent.capabilities.dirs", "");
        var sessionCapRegistry = buildCapabilityRegistry(effectiveCapDirs, "");
        if (sessionCapRegistry != null && this.capabilityRegistry == null) {
            selectedTools.register(ToolRegistry.createMethod(
                new CapabilitySearchTool(sessionCapRegistry, model)));
        }

        var selectedSkills = req.skills != null && !req.skills.isEmpty()
            ? allSkills.stream().filter(s -> req.skills.contains(s.name())).toList()
            : allSkills;

        var effectiveInitialSkills = req.initialSkills != null ? req.initialSkills : config.initialSkills();

        boolean effectiveSkillSearch = req.skillSearchEnabled != null ? req.skillSearchEnabled : config.skillSearchEnabled();
        var plugins = AgentFactory.buildPlugins(selectedSkills, effectiveInitialSkills, effectiveSkillSearch);

        var modelToUse = wrapModel(model);
        var streamingModel = findStreamingModel();

        var mcpClients = new ArrayList<McpClient>();
        // Multi-server: iterate over mcpServers list
        if (req.mcpServers != null && !req.mcpServers.isEmpty()) {
            for (var sel : req.mcpServers) {
                var mcpCfg = resolveServerConfig(sel);
                if (mcpCfg == null) continue;
                var selectedMcpToolNames = sel.tools != null && !sel.tools.isEmpty()
                    ? new HashSet<>(sel.tools) : null;
                try {
                    var client = McpConnector.connect(mcpCfg, selectedTools, selectedMcpToolNames);
                    mcpClients.add(client);
                } catch (Exception e) {
                    log.warn("MCP-Verbindung fehlgeschlagen ({}): {}", sel.serverName, e.getMessage());
                }
            }
        } else {
            // Fallback: single server via legacy fields
            var selectedMcpToolNames = req.mcpTools != null && !req.mcpTools.isEmpty()
                ? new HashSet<>(req.mcpTools) : null;
            var mcpServerConfig = capabilityRegistry != null && req.mcpServerName != null && !req.mcpServerName.isBlank()
                ? capabilityRegistry.getServer(req.mcpServerName) : null;
            if (mcpServerConfig != null) {
                try {
                    mcpClients.add(McpConnector.connect(mcpServerConfig, selectedTools, selectedMcpToolNames));
                } catch (Exception e) {
                    log.warn("MCP-Verbindung fehlgeschlagen: {}", e.getMessage());
                }
            }
        }

        var systemPrompt = buildSystemPrompt(req.systemPrompt, selectedTools, selectedSkills);
        Agent agent;
        if (streamingModel != null) {
            //TODO configurable
            ResilienceConfig resilienceConfig = new ResilienceConfig(
                    new RetryConfig(3, 1000, 2.0), // 3 retries, starting at 1s, doubling each time
                    new CircuitBreakerConfig(0.5f, 10L, 30L) // 50% failure rate, 10s window, 30s half-open
            );
            SummarizingConversationManager conversationManager = new SummarizingConversationManager(
                    ModelFactory.createOpenAiFromEnv(), 2048);
            agent = new StreamingAgent(streamingModel, selectedTools, new ToolExecutor(),
                    conversationManager, sessionManager, resilienceConfig, plugins);
        } else {
            agent = new Agent(modelToUse, selectedTools, new ToolExecutor(),
                null, sessionManager, null, plugins);
        }
        agent.setSystemPrompt(systemPrompt);
        agent.setSessionId(sessionId);

        var phases = new CopyOnWriteArrayList<String>();
        agent.addEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });


        initializedAgents.put(sessionId, new InitializedSession(agent, selectedTools, mcpClients, phases, streamingModel, systemPrompt));

        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = new ChatResponse();
        resp.answer = "Agent initialisiert";
        resp.sessionId = sessionId;
        resp.durationMs = durationMs;
        resp.toolCallsCount = selectedTools.size();
        return resp;
    }

    public ChatResponse chat(ChatRequest req) {
        ensureInitialized();
        var session = req.sessionId != null ? initializedAgents.get(req.sessionId) : null;
        if (session != null) {
            return chatWithInit(req, session);
        }
        return chatWithoutInit(req);
    }

    private ChatResponse chatWithoutInit(ChatRequest req) {
        var start = System.nanoTime();
        var activeTools = filterTools(req);
        var activeSkills = filterSkills(req);
        var plugins = buildPlugins(activeSkills, config.initialSkills());
        var modelToUse = wrapModel(model);
        var agent = AgentFactory.createAgent(modelToUse, activeTools, sessionManager, null, plugins);
        var phases = new CopyOnWriteArrayList<String>();
        agent.addEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }
        var result = agent.execute(req.sessionId, req.prompt);
        var durationMs = (System.nanoTime() - start) / 1_000_000;
        return buildChatResponse(result, durationMs, phases, new ConcurrentHashMap<>());
    }

    private ChatResponse chatWithInit(ChatRequest req, InitializedSession session) {
        var start = System.nanoTime();
        var phases = session.phases();
        var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
        var agent = session.agent();
        AgentEventListener listener = event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            } else if (event instanceof ToolExecutionStartedEvent te) {
                var tc = te.toolCall();
                toolCallMap.put(tc.id(), new ToolCallCapture(tc.toolName(), tc.arguments()));
            } else if (event instanceof ToolExecutionFinishedEvent te) {
                var existing = toolCallMap.get(te.result().toolCallId());
                if (existing != null) {
                    existing.result = te.result().result();
                    existing.isError = te.result().isError();
                }
            }
        };
        agent.addEventListener(listener);
        try {
            var result = agent.execute(req.sessionId, req.prompt);
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            return buildChatResponse(result, durationMs, phases, toolCallMap);
        } finally {
            agent.removeEventListener(listener);
        }
    }

    public void chatSse(ChatRequest req,
                         Consumer<String> onToken,
                         Consumer<List<String>> onPhases,
                         Consumer<ChatResponse> onComplete) {
        ensureInitialized();

        var initializedSession = req.sessionId != null ? initializedAgents.get(req.sessionId) : null;

        ToolRegistry activeTools;
        List<Plugin> plugins;
        StreamingChatModel streamingModelToUse;

        if (initializedSession != null) {
            activeTools = initializedSession.registry();
            streamingModelToUse = initializedSession.streamingModel() != null
                ? initializedSession.streamingModel() : findStreamingModel();
            // Use same plugins/skills setup logic as in initAgent for consistency
            plugins = initializedSession.agent().getPlugins();
        } else {
            activeTools = filterTools(req);
            var activeSkills = filterSkills(req);
            plugins = buildPlugins(activeSkills, config.initialSkills());
            streamingModelToUse = findStreamingModel();
        }

        var start = System.nanoTime();
        StreamingAgent agent;

        if (initializedSession != null && initializedSession.agent() instanceof StreamingAgent sa) {
            agent = sa;
        } else {
            if (streamingModelToUse != null) {
                agent = new StreamingAgent(streamingModelToUse,
                    activeTools, new ToolExecutor(), null, sessionManager, null, plugins);
            } else {
                agent = new StreamingAgent(
                    new MockStreamingChatModel(),
                    activeTools, new ToolExecutor());
            }
        }

        if (initializedSession != null && initializedSession.systemPrompt() != null) {
            agent.setSystemPrompt(initializedSession.systemPrompt());
        } else if (req.systemPrompt != null && !req.systemPrompt.isBlank()) {
            agent.setSystemPrompt(buildSystemPrompt(req.systemPrompt, activeTools, List.of()));
        }

        var phases = initializedSession != null ? initializedSession.phases() : new CopyOnWriteArrayList<String>();
        var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }
        if (sseChannel != null) {
            sseChannel.register(req.sessionId, msg -> onToken.accept(
                "{\"type\":\"checkpoint\",\"data\":" + msg + "}"));
        }
        AgentEventListener listener = event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            } else if (event instanceof ToolExecutionStartedEvent te) {
                var tc = te.toolCall();
                toolCallMap.put(tc.id(), new ToolCallCapture(tc.toolName(), tc.arguments()));
            } else if (event instanceof ToolExecutionFinishedEvent te) {
                var existing = toolCallMap.get(te.result().toolCallId());
                if (existing != null) {
                    existing.result = te.result().result();
                    existing.isError = te.result().isError();
                }
            }
        };
        agent.addEventListener(listener);
        try {
            var result = agent.executeStreaming(req.sessionId, req.prompt, onToken);
            if (sseChannel != null) {
                sseChannel.unregister(req.sessionId);
            }
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            if (onPhases != null) onPhases.accept(List.copyOf(phases));
            var resp = buildChatResponse(result, durationMs, phases, toolCallMap);
            resp.thinking = agent.getLastThinking();
            if (onComplete != null) onComplete.accept(resp);
        } finally {
            agent.removeEventListener(listener);
        }
    }

    public SSEChannel getSseChannel() {
        ensureInitialized();
        return sseChannel;
    }

    public Agent createDefaultAgent() {
        ensureInitialized();
        var selectedTools = fullRegistry.withOnly(new HashSet<>(fullRegistry.getToolNames()));
        var modelToUse = wrapModel(model);
        var plugins = buildPlugins(allSkills, config.initialSkills());
        return AgentFactory.createAgent(modelToUse, selectedTools, sessionManager, null, plugins);
    }

    public void releaseSession(String sessionId) {
        var session = initializedAgents.remove(sessionId);
        if (session != null) {
            session.close();
        }
    }

    public ChatResponse agentDemo(ChatRequest req) {
        // 1. ChatModel
        ChatModel model = createModel(); // Reusing the service's model creation logic

        // 2. CheckpointService
        CheckpointService cpService = new CheckpointService(
            System.getenv("STRANDS_AGENT_HITL_TOOLS"), 120_000);
        cpService.registerChannel(new ConsoleChannel());
        var cpHook = new CheckpointHook(cpService);

        // 3. ToolRegistry
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new BashTool(Path.of("")));
        toolRegistry.register(new ReadTool(Path.of("")));
        toolRegistry.register(new HumanInTheLoopTool(cpService));

        // 4. ToolExecutor
        ToolExecutor toolExecutor = new ToolExecutor();

        // 5. ConversationManager
        ConversationManager conversationManager = new SlidingWindowConversationManager(10);

        // 6. SessionManager
        SessionManager sessionManager = new FileSessionManager(Path.of("logs/sessions"));

        // 7. ResilienceConfig
        ResilienceConfig resilienceConfig = new ResilienceConfig(
            new RetryConfig(3, 1000, 2.0),
            new CircuitBreakerConfig(0.5f, 10L, 30L)
        );

        // 8. Plugins
        GuardrailPlugin guardrails = new GuardrailPlugin(
            List.of((messages, context) -> GuardrailResult.ok()),
            List.of((messages, context) -> GuardrailResult.ok())
        );

        List<Plugin> plugins = List.of(guardrails);

        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            sessionManager,
            resilienceConfig,
            plugins
        );

        agent.setCheckpointService(cpService);
        agent.addHook(cpHook);
        cpHook.setAgent(agent);

        agent.setSystemPrompt("You are a highly capable and secure assistant. " +
                "Always verify actions with the user when using tools.");

        var start = System.nanoTime();
        var result = agent.execute(req.prompt);
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = buildChatResponse(result, durationMs, new CopyOnWriteArrayList<>(), new ConcurrentHashMap<>());
        resp.thinking = agent.getLastThinking();
        return resp;
    }

    private ChatResponse buildChatResponse(
            de.augmentia.strandsagents.core.model.agent.AgentResult result,
            long durationMs,
            List<String> phases,
            ConcurrentHashMap<String, ToolCallCapture> toolCallMap) {
        var resp = new ChatResponse();
        resp.answer = result.finalAnswer();
        resp.sessionId = result.sessionId();
        resp.stopReason = result.stopReason();
        resp.durationMs = durationMs;
        resp.inputTokens = result.metrics().inputTokens();
        resp.outputTokens = result.metrics().outputTokens();
        resp.toolCallsCount = result.metrics().toolCallsCount();
        resp.phases = List.copyOf(phases);
        resp.toolCalls = toolCallMap.values().stream()
            .map(tc -> tc.toInfo(TOOL_CALL_MAPPER))
            .toList();
        resp.memoryUsed = result.metrics().inputTokens() > 0 && result.metrics().toolCallsCount() > 0;
        resp.memorySources = List.of();
        return resp;
    }

    public ToolRegistry getFullRegistry() {
        ensureInitialized();
        return fullRegistry;
    }

    public List<Skill> getAllSkills() {
        ensureInitialized();
        return allSkills;
    }

    public SessionManager getSessionManager() {
        ensureInitialized();
        return sessionManager;
    }

    public ChatModel getModel() {
        ensureInitialized();
        return model;
    }

    public dev.langchain4j.model.chat.StreamingChatModel getStreamingModel() {
        ensureInitialized();
        return findStreamingModel();
    }

    public List<ToolInfo> listTools() {
        ensureInitialized();
        return fullRegistry.getToolNames().stream()
            .map(name -> {
                var info = new ToolInfo();
                info.name = name;
                try {
                    var spec = fullRegistry.get(name).spec();
                    info.description = spec.description();
                    info.parameters = spec.parameters().toString();
                } catch (Exception e) {
                    info.description = "";
                    info.parameters = "";
                }
                return info;
            })
            .toList();
    }

    public List<SkillInfo> listSkills() {
        ensureInitialized();
        return allSkills.stream()
            .map(s -> {
                var info = new SkillInfo();
                info.name = s.name();
                info.description = s.description();
                return info;
            })
            .toList();
    }

    private static String mcpPrefix(CapabilityRegistry.McpServerConfig config) {
        return McpConnector.prefix(config);
    }

    public List<Map<String, String>> getMcpServers() {
        if (capabilityRegistry == null) return List.of();
        return capabilityRegistry.mcpServers().stream()
            .map(s -> Map.of("name", s.name(), "type", s.url() != null ? "http" : "stdio"))
            .toList();
    }

    public List<ToolInfo> discoverMcpTools(String serverName) {
        if (capabilityRegistry == null) return List.of();
        var config = capabilityRegistry.getServer(serverName);
        if (config == null) return List.of();
        return McpConnector.discoverTools(config);
    }

    public List<ToolInfo> connectMcpUrl(String url, String serverName) {
        var name = (serverName != null && !serverName.isBlank()) ? serverName : "custom";
        var config = new CapabilityRegistry.McpServerConfig(name, url);
        return McpConnector.discoverTools(config);
    }

    private CapabilityRegistry.McpServerConfig resolveServerConfig(McpServerSelection sel) {
        if (sel.url != null && !sel.url.isBlank()) {
            var name = (sel.serverName != null && !sel.serverName.isBlank()) ? sel.serverName : "custom";
            return new CapabilityRegistry.McpServerConfig(name, sel.url);
        }
        if (capabilityRegistry != null && sel.serverName != null && !sel.serverName.isBlank()) {
            return capabilityRegistry.getServer(sel.serverName);
        }
        return null;
    }

    private McpClient connectMcp(CapabilityRegistry.McpServerConfig config, ToolRegistry registry, Set<String> selectedTools) throws Exception {
        return McpConnector.connect(config, registry, selectedTools);
    }

    private ToolRegistry filterTools(ChatRequest req) {
        if (req.tools == null || req.tools.isEmpty()) {
            return fullRegistry;
        }
        return fullRegistry.withOnly(new HashSet<>(req.tools));
    }

    private List<Skill> filterSkills(ChatRequest req) {
        if (req.skills == null || req.skills.isEmpty()) {
            return allSkills;
        }
        var selected = new HashSet<>(req.skills);
        return allSkills.stream()
            .filter(s -> selected.contains(s.name()))
            .toList();
    }

    static String buildSystemPrompt(String requestedPrompt, ToolRegistry selectedTools, List<Skill> selectedSkills) {
        var basePrompt = requestedPrompt != null && !requestedPrompt.isBlank()
            ? requestedPrompt.strip()
            : DEFAULT_SYSTEM_PROMPT;
        var toolSection = describeTools(selectedTools);
        var skillSection = describeSkills(selectedSkills);
        var hasToolsPlaceholder = basePrompt.contains(TOOLS_PLACEHOLDER);
        var hasSkillsPlaceholder = basePrompt.contains(SKILLS_PLACEHOLDER);

        var prompt = basePrompt
            .replace(TOOLS_PLACEHOLDER, toolSection)
            .replace(SKILLS_PLACEHOLDER, skillSection);

        var appended = new StringBuilder(prompt);
        if (!hasToolsPlaceholder) {
            appended.append("\n\nSelected tools:\n").append(toolSection);
        }
        if (!hasSkillsPlaceholder) {
            appended.append("\n\nSelected skills:\n").append(skillSection);
        }
        return appended.toString();
    }

    private static String describeTools(ToolRegistry selectedTools) {
        if (selectedTools == null || selectedTools.size() == 0) {
            return "- No tools selected.";
        }
        return selectedTools.getToolNames().stream()
            .sorted()
            .map(name -> {
                try {
                    var description = selectedTools.get(name).spec().description();
                    return "- " + name + formatDescription(description);
                } catch (Exception e) {
                    return "- " + name;
                }
            })
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String describeSkills(List<Skill> selectedSkills) {
        if (selectedSkills == null || selectedSkills.isEmpty()) {
            return "- No skills selected.";
        }
        return selectedSkills.stream()
            .sorted(Comparator.comparing(Skill::name))
            .map(skill -> "- " + skill.name() + formatDescription(skill.description()))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String formatDescription(String description) {
        return description != null && !description.isBlank() ? ": " + description.strip() : "";
    }

    private List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills) {
        return AgentFactory.buildPlugins(skills, initialSkills, config.skillSearchEnabled());
    }

    public boolean isRuntimeKeyActive() {
        return secretService.isRuntimeKeyActive();
    }

    public CheckpointService getCheckpointService() {
        return checkpointService;
    }

    public synchronized void activateModel(String apiKey) {
        secretService.setRuntimeApiKey(apiKey);
        ensureInitialized();
        this.model = createModel();
        log.info("API-Key aktiviert – Model neu erstellt");
    }

    public synchronized void deactivateModel() {
        secretService.clearRuntimeApiKey();
        ensureInitialized();
        this.model = createModel();
        log.info("API-Key deaktiviert – MockChatModel aktiv");
    }

    private ChatModel createModel() {
        try {
            var apiKey = secretService.getOpenAiApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                return ModelFactory.createOpenAiFromEnv(apiKey);
            }
        } catch (Exception e) {
            log.debug("Model creation via secret service failed: {}", e.getMessage());
        }
        var mock = new MockChatModel();
        log.warn("OPENAI_API_KEY nicht gesetzt – nutze MockChatModel");
        return mock;
    }

    private dev.langchain4j.model.chat.StreamingChatModel findStreamingModel() {
        try {
            var apiKey = secretService.getOpenAiApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                return ModelFactory.createOpenAiStreamingFromEnv(apiKey);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private ChatModel wrapModel(ChatModel m) {
        if (!config.llmLogEnabled()) return m;
        try {
            Files.createDirectories(logDir);
            var logger = new FileLlmLogger(Path.of(config.llmLogPath()));
            var wrapped = new LoggingChatModel(m, logger);
            Runtime.getRuntime().addShutdownHook(new Thread(logger::close));
            return wrapped;
        } catch (Exception e) {
            log.warn("LLM-Logging nicht verfügbar: {}", e.getMessage());
            return m;
        }
    }

    private List<Skill> loadSkills() {
        var dir = Path.of(config.skillsDir());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try {
            return SkillParser.fromDirectory(dir);
        } catch (Exception e) {
            log.warn("Skills nicht ladbar: {}", e.getMessage());
            return List.of();
        }
    }

    private CapabilityRegistry buildCapabilityRegistry() {
        var configPath = config.mcpConfigPath();
        var mcpServers = McpServerConfigLoader.load(Path.of(configPath));

        var dirsProp = System.getProperty("strands.agent.capabilities.dirs",
            System.getenv().getOrDefault("STRANDS_CAPABILITIES_DIRS", ""));
        var builder = CapabilityRegistry.builder();
        for (var s : mcpServers) {
            builder.mcpServer(s);
        }
        if (dirsProp != null && !dirsProp.isBlank()) {
            for (var d : dirsProp.split(",")) {
                var dir = Path.of(d.strip());
                if (Files.isDirectory(dir)) {
                    builder.skillDir(dir);
                }
            }
        }
        return mcpServers.isEmpty() && (dirsProp == null || dirsProp.isBlank()) ? null : builder.build();
    }

    private CapabilityRegistry buildCapabilityRegistry(String dirsProp, String mcpProp) {
        if ((dirsProp == null || dirsProp.isBlank()) && (mcpProp == null || mcpProp.isBlank())) return null;
        var builder = CapabilityRegistry.builder();
        if (dirsProp != null && !dirsProp.isBlank()) {
            for (var d : dirsProp.split(",")) {
                var dir = Path.of(d.strip());
                if (Files.isDirectory(dir)) {
                    builder.skillDir(dir);
                }
            }
        }
        return builder.build();
    }

    private void setupLogging() {
        try {
            Files.createDirectories(logDir);
        } catch (Exception ignored) {}
    }
}
