package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.MockStreamingChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointHook;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.CheckpointService;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.ConsoleChannel;
import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.SSEChannel;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import de.augmentia.strandsagents.core.tools.McpToolMethod;
import de.augmentia.strandsagents.skills.*;
import dev.langchain4j.mcp.client.McpClient;
import de.augmentia.strandsagents.quarkus.dto.AgentInitRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatResponse;
import de.augmentia.strandsagents.quarkus.dto.McpServerSelection;
import de.augmentia.strandsagents.quarkus.dto.SkillInfo;
import de.augmentia.strandsagents.quarkus.dto.ToolInfo;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import dev.langchain4j.agent.tool.ToolSpecification;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionFinishedEvent;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.local.HttpTool;
import de.augmentia.strandsagents.core.tools.local.BashTool;
import de.augmentia.strandsagents.core.tools.HumanInTheLoopTool;
import de.augmentia.strandsagents.core.tools.local.ReadTool;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    @Inject
    SecretService secretService;

    private ToolRegistry fullRegistry;
    private List<Skill> allSkills;
    private ChatModel model;
    private FileSessionManager sessionManager;
    private Path logDir;
    private Path skillsDir;
    private Path sessionDir;
    private String skillsDirProp;
    private String sessionDirProp;
    private boolean llmLogEnabledProp;
    private String llmLogPathProp;

    private List<String> initialSkills;
    private boolean skillSearchEnabled;
    private boolean mcpIngestEnabled;
    private CapabilityRegistry capabilityRegistry;
    private CheckpointService checkpointService;
    private SSEChannel sseChannel;

    private final ConcurrentHashMap<String, InitializedSession> initializedAgents = new ConcurrentHashMap<>();

    private record InitializedSession(
        Agent agent,
        ToolRegistry registry,
        List<McpClient> mcpClients,
        List<String> phases,
        StreamingChatModel streamingModel
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
        this.skillsDirProp = System.getProperty("strands.agent.skills.dir",
            System.getenv().getOrDefault("STRANDS_SKILLS_DIR", "skills"));
        this.sessionDirProp = System.getProperty("strands.agent.session.dir",
            System.getenv().getOrDefault("STRANDS_SESSION_DIR", ".sessions"));
        this.llmLogEnabledProp = Boolean.parseBoolean(System.getProperty("strands.agent.llm-log.enabled",
            System.getenv().getOrDefault("STRANDS_LLM_LOG_ENABLED", "true")));
        this.llmLogPathProp = System.getProperty("strands.agent.llm-log.path",
            System.getenv().getOrDefault("STRANDS_LLM_LOG_PATH", "logs/llm-calls.log"));
        this.skillsDir = Path.of(skillsDirProp);
        this.sessionDir = Path.of(sessionDirProp);

        var initialProp = System.getProperty("strands.agent.skills.initial",
            System.getenv().getOrDefault("STRANDS_SKILLS_INITIAL", ""));
        this.initialSkills = initialProp.isBlank() ? List.of()
            : List.of(initialProp.split(",")).stream().map(String::strip).filter(s -> !s.isEmpty()).toList();

        this.skillSearchEnabled = Boolean.parseBoolean(System.getProperty("strands.agent.skills.search",
            System.getenv().getOrDefault("STRANDS_SKILLS_SEARCH", "false")));

        this.mcpIngestEnabled = Boolean.parseBoolean(System.getProperty("strands.agent.mcp.ingest",
            System.getenv().getOrDefault("STRANDS_MCP_INGEST", "false")));

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
        this.fullRegistry = createFullRegistry();
        this.allSkills = loadSkills();
        this.sessionManager = createSessionManager();
        this.logDir = Path.of(llmLogPathProp).getParent();

        this.checkpointService = new CheckpointService(System.getenv("STRANDS_AGENT_HITL_TOOLS"), 120_000);
        this.checkpointService.registerChannel(new ConsoleChannel());
        this.sseChannel = new SSEChannel();
        this.checkpointService.registerChannel(sseChannel);

        if (mcpIngestEnabled) {
            fullRegistry.register(new McpIngestTool(fullRegistry));
        }
        if (capabilityRegistry != null) {
            fullRegistry.register(ToolRegistry.createMethod(
                new CapabilitySearchTool(capabilityRegistry, model)));
        }

        setupLogging();
    }

    public ChatResponse initAgent(AgentInitRequest req) {
        ensureInitialized();
        var sessionId = UUID.randomUUID().toString();
        var start = System.nanoTime();

        var selectedTools = req.tools != null && !req.tools.isEmpty()
            ? fullRegistry.withOnly(new HashSet<>(req.tools))
            : fullRegistry.withOnly(new HashSet<>());

        // Mode 2: add MCP ingest tool per-session
        boolean effectiveMcpIngest = req.mcpIngestEnabled != null ? req.mcpIngestEnabled : this.mcpIngestEnabled;
        if (effectiveMcpIngest && !this.mcpIngestEnabled) {
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

        var effectiveInitialSkills = req.initialSkills != null ? req.initialSkills : initialSkills;

        boolean effectiveSkillSearch = req.skillSearchEnabled != null ? req.skillSearchEnabled : this.skillSearchEnabled;
        var plugins = buildPlugins(selectedSkills, effectiveInitialSkills, effectiveSkillSearch);

        var modelToUse = wrapModel(model);
        var streamingModel = findStreamingModel();
        var agent = new Agent(modelToUse, selectedTools, new ToolExecutor(),
            null, sessionManager, null, plugins);

        var cpService = new CheckpointService(System.getenv("STRANDS_AGENT_HITL_TOOLS"), 120_000);
        cpService.registerChannel(new ConsoleChannel());
        if (sseChannel != null) cpService.registerChannel(sseChannel);
        var cpHook = new CheckpointHook(cpService);
        agent.setCheckpointService(cpService);
        agent.addHook(cpHook);
        cpHook.setAgent(agent);

        var phases = new CopyOnWriteArrayList<String>();
        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });

        var mcpClients = new ArrayList<McpClient>();
        // Multi-server: iterate over mcpServers list
        if (req.mcpServers != null && !req.mcpServers.isEmpty()) {
            for (var sel : req.mcpServers) {
                var config = resolveServerConfig(sel);
                if (config == null) continue;
                var selectedMcpToolNames = sel.tools != null && !sel.tools.isEmpty()
                    ? new HashSet<>(sel.tools) : null;
                try {
                    var client = connectMcp(config, selectedTools, selectedMcpToolNames);
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
                    mcpClients.add(connectMcp(mcpServerConfig, selectedTools, selectedMcpToolNames));
                } catch (Exception e) {
                    log.warn("MCP-Verbindung fehlgeschlagen: {}", e.getMessage());
                }
            }
        }

        initializedAgents.put(sessionId, new InitializedSession(agent, selectedTools, mcpClients, phases, streamingModel));

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

        var start = System.nanoTime();
        var activeTools = filterTools(req);
        var activeSkills = filterSkills(req);
        var plugins = buildPlugins(activeSkills, initialSkills);
        var modelToUse = wrapModel(model);

        var agent = new Agent(modelToUse, activeTools, new ToolExecutor(),
            null, sessionManager, null, plugins);

        var phases = new CopyOnWriteArrayList<String>();
        var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        agent.setEventListener(event -> {
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
        });

        var result = agent.execute(req.sessionId, req.prompt, Map.of());
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = buildChatResponse(result, durationMs, phases, toolCallMap);
        return resp;
    }

    private ChatResponse chatWithInit(ChatRequest req, InitializedSession session) {
        var start = System.nanoTime();

        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
        var currentPhases = session.phases();
        currentPhases.clear();

        var agent = session.agent();
        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                currentPhases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
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
        });

        var result = agent.execute(req.sessionId, req.prompt, Map.of());
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = buildChatResponse(result, durationMs, currentPhases, toolCallMap);
        return resp;
    }

    public void chatSse(ChatRequest req, java.util.function.Consumer<String> onToken,
                         java.util.function.Consumer<List<String>> onPhases,
                         java.util.function.Consumer<ChatResponse> onComplete) {
        ensureInitialized();
        var start = System.nanoTime();

        var session = req.sessionId != null ? initializedAgents.get(req.sessionId) : null;
        if (session != null) {
            var phases = new CopyOnWriteArrayList<String>();
            var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
            var sModel = session.streamingModel();
            StreamingAgent sAgent;
            if (sModel != null) {
                sAgent = new StreamingAgent(sModel,
                    session.registry(), new ToolExecutor(), null, sessionManager, null);
            } else {
                sAgent = new StreamingAgent(
                    new MockStreamingChatModel(),
                    session.registry(), new ToolExecutor());
            }
            if (req.sessionId == null) {
                req.sessionId = UUID.randomUUID().toString();
            }
            if (sseChannel != null) {
                var sid = req.sessionId;
                sseChannel.register(sid, msg -> onToken.accept(
                    "{\"type\":\"checkpoint\",\"data\":" + msg + "}"));
            }
            sAgent.setEventListener(event -> {
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
            });
            var result = sAgent.executeStreaming(req.prompt, onToken);
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            if (onPhases != null) onPhases.accept(List.copyOf(phases));
            var resp = buildChatResponse(result, durationMs, phases, toolCallMap);
            if (onComplete != null) onComplete.accept(resp);
            if (sseChannel != null) sseChannel.unregister(req.sessionId);
            return;
        }

        var activeTools = filterTools(req);
        var activeSkills = filterSkills(req);
        var plugins = buildPlugins(activeSkills, initialSkills);
        var modelToUse = wrapModel(model);

        var streamingModel = findStreamingModel();
        StreamingAgent agent;
        if (streamingModel != null) {
            agent = new StreamingAgent(streamingModel,
                activeTools, new ToolExecutor(), null, sessionManager, null);
        } else {
            agent = new StreamingAgent(
                new MockStreamingChatModel(),
                activeTools, new ToolExecutor());
        }

        var phases = new CopyOnWriteArrayList<String>();
        var toolCallMap = new ConcurrentHashMap<String, ToolCallCapture>();
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        if (sseChannel != null) {
            sseChannel.register(req.sessionId, msg -> onToken.accept(
                "{\"type\":\"checkpoint\",\"data\":" + msg + "}"));
        }

        agent.setEventListener(event -> {
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
        });

        var result = agent.executeStreaming(req.prompt, onToken);
        if (sseChannel != null) {
            sseChannel.unregister(req.sessionId);
        }
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        if (onPhases != null) onPhases.accept(List.copyOf(phases));

        var resp = buildChatResponse(result, durationMs, phases, toolCallMap);
        if (onComplete != null) onComplete.accept(resp);
    }

    public Agent createDefaultAgent() {
        ensureInitialized();
        var selectedTools = fullRegistry.withOnly(new HashSet<>(fullRegistry.getToolNames()));
        var modelToUse = wrapModel(model);
        var plugins = buildPlugins(allSkills, initialSkills);
        return new Agent(modelToUse, selectedTools, new ToolExecutor(),
            null, sessionManager, null, plugins);
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

    public FileSessionManager getSessionManager() {
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
        return "mcp_" + config.name().replaceAll("[^a-zA-Z0-9]", "_");
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
        return discoverToolsFromConfig(config);
    }

    public List<ToolInfo> connectMcpUrl(String url, String serverName) {
        var name = (serverName != null && !serverName.isBlank()) ? serverName : "custom";
        var config = new CapabilityRegistry.McpServerConfig(name, url);
        return discoverToolsFromConfig(config);
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

    private List<ToolInfo> discoverToolsFromConfig(CapabilityRegistry.McpServerConfig config) {
        try {
            var client = config.toDirectClient();
            var tools = client.listTools();
            client.close();
            var prefix = mcpPrefix(config);
            return tools.stream()
                .map(spec -> {
                    var info = new ToolInfo();
                    info.name = prefix + "_" + spec.name();
                    info.description = spec.description() != null ? spec.description() : "";
                    info.parameters = spec.parameters() != null ? spec.parameters().toString() : "";
                    return info;
                })
                .toList();
        } catch (Exception e) {
            log.warn("MCP-Verbindung fehlgeschlagen: " + e.getMessage());
            return List.of();
        }
    }

    private McpClient connectMcp(CapabilityRegistry.McpServerConfig config, ToolRegistry registry, Set<String> selectedTools) throws Exception {
        var client = config.toDirectClient();
        var tools = client.listTools();
        var prefix = mcpPrefix(config);
        int registered = 0;
        for (var spec : tools) {
            var prefixedName = prefix + "_" + spec.name();
            if (selectedTools != null && !selectedTools.contains(prefixedName)) continue;
            var prefixedSpec = ToolSpecification.builder()
                .name(prefixedName)
                .description(spec.description())
                .parameters(spec.parameters())
                .build();
            registry.register(prefixedName, prefixedSpec,
                new McpToolMethod(client, config.name(), spec.name(), prefixedSpec));
            registered++;
        }
        log.info("MCP verbunden: {} ({}/{} Tools registriert)", config.name(), registered, tools.size());
        return client;
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

    private List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills) {
        return buildPlugins(skills, initialSkills, this.skillSearchEnabled);
    }

    private List<Plugin> buildPlugins(List<Skill> skills, List<String> initialSkills, boolean skillSearchEnabled) {
        var plugins = new ArrayList<Plugin>();
        if (!skills.isEmpty()) {
            var skillsPlugin = new AgentSkillsPlugin(skills, initialSkills);
            skillsPlugin.setSkillSearchEnabled(skillSearchEnabled);
            plugins.add(skillsPlugin);
        }
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        return plugins;
    }

    public boolean isRuntimeKeyActive() {
        return secretService.isRuntimeKeyActive();
    }

    public CheckpointService getCheckpointService() {
        return checkpointService;
    }

    public SSEChannel getSseChannel() {
        return sseChannel;
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
        if (!llmLogEnabledProp) return m;
        try {
            Files.createDirectories(logDir);
            var logger = new FileLlmLogger(Path.of(llmLogPathProp));
            var wrapped = new LoggingChatModel(m, logger);
            Runtime.getRuntime().addShutdownHook(new Thread(logger::close));
            return wrapped;
        } catch (Exception e) {
            log.warn("LLM-Logging nicht verfügbar: {}", e.getMessage());
            return m;
        }
    }

    private ToolRegistry createFullRegistry() {
        var workspaceProp = System.getProperty("strands.agent.workspace",
            System.getenv().getOrDefault("STRANDS_AGENT_WORKSPACE", ""));
        var workspace = workspaceProp.isBlank() ? Path.of("").toAbsolutePath()
            : Path.of(workspaceProp).toAbsolutePath();

        var bashAllowed = Boolean.parseBoolean(System.getProperty("strands.agent.bash.allow",
            System.getenv().getOrDefault("STRANDS_AGENT_BASH_ALLOW", "false")));

        var blockHttpPrivate = !Boolean.parseBoolean(System.getProperty("strands.agent.http.allow-private",
            System.getenv().getOrDefault("STRANDS_AGENT_HTTP_ALLOW_PRIVATE", "false")));

        var extraTools = System.getProperty("strands.agent.tools",
            System.getenv().getOrDefault("STRANDS_AGENT_TOOLS", ""));

        var builder = ToolRegistry.builder()
            .standard(bashAllowed)
            .workspace(workspace);

        builder.with(new HttpTool(blockHttpPrivate));

        if (!extraTools.isBlank()) {
            for (var cn : extraTools.split(",")) {
                cn = cn.strip();
                if (!cn.isEmpty()) builder.with(cn);
            }
        }
        var registry = builder.build();
        registry.register(new ListToolsTool(registry));
        return registry;
    }

    private List<Skill> loadSkills() {
        var dir = Path.of(skillsDirProp);
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
        var configPath = System.getProperty("strands.agent.mcp.config",
            System.getenv().getOrDefault("STRANDS_MCP_CONFIG", "config/MCP_SERVER_CONFIG.json"));
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

    private FileSessionManager createSessionManager() {
        try {
            Files.createDirectories(sessionDir);
        } catch (Exception ignored) {}
        return new FileSessionManager(sessionDir);
    }

    private void setupLogging() {
        try {
            Files.createDirectories(logDir);
        } catch (Exception ignored) {}
    }
}
