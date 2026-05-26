package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.MockChatModel;
import de.augmentia.strandsagents.core.agent.MockStreamingChatModel;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.config.LlmConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.logging.FileLlmLogger;
import de.augmentia.strandsagents.core.logging.LoggingChatModel;
import de.augmentia.strandsagents.core.model.event.AgentStateChangedEvent;
import de.augmentia.strandsagents.core.plugin.Plugin;
import de.augmentia.strandsagents.core.plugin.guardrail.ApprovalResult;
import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.HITLAuthority;
import de.augmentia.strandsagents.core.plugin.hitl.HITLPlugin;
import de.augmentia.strandsagents.core.plugin.hitl.HITLProvider;
import de.augmentia.strandsagents.core.tools.ListToolsTool;
import de.augmentia.strandsagents.core.tools.McpToolMethod;
import de.augmentia.strandsagents.skills.*;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import de.augmentia.strandsagents.quarkus.dto.AgentInitRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatRequest;
import de.augmentia.strandsagents.quarkus.dto.ChatResponse;
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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.augmentia.strandsagents.core.plugin.guardrail.GuardrailResult;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.BashTool;
import de.augmentia.strandsagents.core.tools.HumanInTheLoopTool;
import de.augmentia.strandsagents.core.tools.ReadTool;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.sessions.SessionManager;

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
    private String defaultMcpUrl;

    private final ConcurrentHashMap<String, InitializedSession> initializedAgents = new ConcurrentHashMap<>();

    private record InitializedSession(
        Agent agent,
        ToolRegistry registry,
        McpClient mcpClient,
        List<String> phases,
        StreamingChatModel streamingModel
    ) {
        void close() {
            if (mcpClient != null) try { mcpClient.close(); } catch (Exception ignored) {}
        }
    }

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
        this.defaultMcpUrl = System.getProperty("strands.agent.mcp.url",
            System.getenv().getOrDefault("STRANDS_MCP_URL", "http://localhost:8888/mcp"));
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
            : fullRegistry.withOnly(new HashSet<>(fullRegistry.getToolNames()));

        // Mode 2: add MCP ingest tool per-session
        boolean effectiveMcpIngest = req.mcpIngestEnabled != null ? req.mcpIngestEnabled : this.mcpIngestEnabled;
        if (effectiveMcpIngest && !this.mcpIngestEnabled) {
            selectedTools.register(new McpIngestTool(selectedTools));
        }

        // Mode 3: add capability search tool per-session
        var effectiveCapDirs = req.capabilityDirs != null ? req.capabilityDirs
            : System.getProperty("strands.agent.capabilities.dirs", "");
        var effectiveCapMcp = req.capabilityMcp != null ? req.capabilityMcp
            : System.getProperty("strands.agent.capabilities.mcp", "");
        var sessionCapRegistry = buildCapabilityRegistry(effectiveCapDirs, effectiveCapMcp);
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

        var phases = new CopyOnWriteArrayList<String>();
        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });

        McpClient mcpClient = null;
        var mcpUrl = req.mcpUrl != null && !req.mcpUrl.isBlank() ? req.mcpUrl : defaultMcpUrl;
        var selectedMcpToolNames = req.mcpTools != null && !req.mcpTools.isEmpty()
            ? new HashSet<>(req.mcpTools) : null;
        if (mcpUrl != null && !mcpUrl.isBlank()) {
            try {
                mcpClient = connectMcp(mcpUrl, selectedTools, selectedMcpToolNames);
            } catch (Exception e) {
                log.warn("MCP-Verbindung fehlgeschlagen: {}", e.getMessage());
            }
        }

        initializedAgents.put(sessionId, new InitializedSession(agent, selectedTools, mcpClient, phases, streamingModel));

        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = new ChatResponse();
        resp.answer = "Agent initialisiert";
        resp.sessionId = sessionId;
        resp.durationMs = durationMs;
        resp.toolCalls = selectedTools.size();
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
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });

        var result = agent.execute(req.sessionId, req.prompt, Map.of());
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = new ChatResponse();
        resp.answer = result.finalAnswer();
        resp.sessionId = result.sessionId();
        resp.stopReason = result.stopReason();
        resp.durationMs = durationMs;
        resp.inputTokens = result.metrics().inputTokens();
        resp.outputTokens = result.metrics().outputTokens();
        resp.toolCalls = result.metrics().toolCallsCount();
        resp.phases = List.copyOf(phases);
        return resp;
    }

    private ChatResponse chatWithInit(ChatRequest req, InitializedSession session) {
        var start = System.nanoTime();

        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        var result = session.agent().execute(req.sessionId, req.prompt, Map.of());
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = new ChatResponse();
        resp.answer = result.finalAnswer();
        resp.sessionId = result.sessionId();
        resp.stopReason = result.stopReason();
        resp.durationMs = durationMs;
        resp.inputTokens = result.metrics().inputTokens();
        resp.outputTokens = result.metrics().outputTokens();
        resp.toolCalls = result.metrics().toolCallsCount();
        resp.phases = List.copyOf(session.phases());
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
            sAgent.setEventListener(event -> {
                if (event instanceof AgentStateChangedEvent sce) {
                    phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
                }
            });
            var result = sAgent.executeStreaming(req.prompt, onToken);
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            if (onPhases != null) onPhases.accept(List.copyOf(phases));
            var resp = new ChatResponse();
            resp.answer = result.finalAnswer();
            resp.sessionId = result.sessionId();
            resp.stopReason = result.stopReason();
            resp.durationMs = durationMs;
            resp.inputTokens = result.metrics().inputTokens();
            resp.outputTokens = result.metrics().outputTokens();
            resp.toolCalls = result.metrics().toolCallsCount();
            resp.phases = List.copyOf(phases);
            if (onComplete != null) onComplete.accept(resp);
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
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "\u2192" + sce.currentPhase());
            }
        });

        var result = agent.executeStreaming(req.prompt, onToken);
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        if (onPhases != null) onPhases.accept(List.copyOf(phases));

        var resp = new ChatResponse();
        resp.answer = result.finalAnswer();
        resp.sessionId = result.sessionId();
        resp.stopReason = result.stopReason();
        resp.durationMs = durationMs;
        resp.inputTokens = result.metrics().inputTokens();
        resp.outputTokens = result.metrics().outputTokens();
        resp.toolCalls = result.metrics().toolCallsCount();
        resp.phases = List.copyOf(phases);
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

        // 2. ToolRegistry
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new BashTool(Path.of("")));
        toolRegistry.register(new ReadTool(Path.of("")));
        toolRegistry.register(new HumanInTheLoopTool());

        // 3. ToolExecutor
        ToolExecutor toolExecutor = new ToolExecutor();

        // 4. ConversationManager
        ConversationManager conversationManager = new SlidingWindowConversationManager(10);

        // 5. SessionManager
        SessionManager sessionManager = new FileSessionManager(Path.of("logs/sessions"));

        // 6. ResilienceConfig
        ResilienceConfig resilienceConfig = new ResilienceConfig(
            new RetryConfig(3, 1000, 2.0),
            new CircuitBreakerConfig(0.5f, 10L, 30L)
        );

        // 7. Plugins
        GuardrailPlugin guardrails = new GuardrailPlugin(
            List.of((messages, context) -> GuardrailResult.ok()),
            List.of((messages, context) -> GuardrailResult.ok())
        );

        HITLPlugin hitl = new HITLPlugin(
            (action, context) -> ApprovalResult.approved(action),
            HITLAuthority.CONFIRM
        );

        List<Plugin> plugins = List.of(guardrails, hitl);

        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            sessionManager,
            resilienceConfig,
            plugins
        );

        agent.setSystemPrompt("You are a highly capable and secure assistant. " +
                "Always verify actions with the user when using tools.");

        var start = System.nanoTime();
        var result = agent.execute(req.prompt);
        var durationMs = (System.nanoTime() - start) / 1_000_000;

        var resp = new ChatResponse();
        resp.answer = result.finalAnswer();
        resp.sessionId = result.sessionId();
        resp.stopReason = result.stopReason();
        resp.durationMs = durationMs;
        resp.inputTokens = result.metrics().inputTokens();
        resp.outputTokens = result.metrics().outputTokens();
        resp.toolCalls = result.metrics().toolCallsCount();
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

    private static String mcpPrefix(String mcpUrl) {
        try {
            var uri = new URI(mcpUrl);
            var host = uri.getHost();
            var port = uri.getPort();
            return "mcp_" + (host != null ? host : "unknown") + (port > 0 ? "_" + port : "");
        } catch (Exception e) {
            return "mcp_" + Math.abs(mcpUrl.hashCode()) % 10000;
        }
    }

    public List<ToolInfo> discoverMcpTools(String mcpUrl) {
        try {
            var transport = StreamableHttpMcpTransport.builder()
                .url(mcpUrl).logRequests(true).logResponses(true).build();
            var client = DefaultMcpClient.builder().transport(transport).build();
            var tools = client.listTools();
            client.close();
            var prefix = mcpPrefix(mcpUrl);
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
            throw new RuntimeException("MCP discovery fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private McpClient connectMcp(String mcpUrl, ToolRegistry registry, Set<String> selectedTools) throws Exception {
        var transport = StreamableHttpMcpTransport.builder()
            .url(mcpUrl).logRequests(true).logResponses(true).build();
        var client = DefaultMcpClient.builder().transport(transport).build();
        var tools = client.listTools();
        var prefix = mcpPrefix(mcpUrl);
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
                new McpToolMethod(client, mcpUrl, spec.name(), prefixedSpec));
            registered++;
        }
        log.info("MCP verbunden: {} ({}/{} Tools registriert)", mcpUrl, registered, tools.size());
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
        var hitlProvider = (HITLProvider) (action, context) -> ApprovalResult.approved(action);
        plugins.add(new HITLPlugin(hitlProvider, HITLAuthority.AUTO));
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        return plugins;
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
        var extraTools = System.getProperty("strands.agent.tools",
            System.getenv().getOrDefault("STRANDS_AGENT_TOOLS", ""));
        var builder = ToolRegistry.builder()
            .standard()
            .cwd(Path.of("").toAbsolutePath());
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
        var dirsProp = System.getProperty("strands.agent.capabilities.dirs",
            System.getenv().getOrDefault("STRANDS_CAPABILITIES_DIRS", ""));
        var mcpProp = System.getProperty("strands.agent.capabilities.mcp",
            System.getenv().getOrDefault("STRANDS_CAPABILITIES_MCP", ""));
        return buildCapabilityRegistry(dirsProp, mcpProp);
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

        if (mcpProp != null && !mcpProp.isBlank()) {
            for (var entry : mcpProp.split(",")) {
                var parts = entry.strip().split(":", 3);
                if (parts.length >= 2) {
                    var name = parts[0].strip();
                    var typeOrUrl = parts[1].strip();
                    if (parts.length == 2) {
                        builder.mcpServer(name, typeOrUrl);
                    } else if ("stdio".equals(typeOrUrl)) {
                        var args = parts[2].strip().split(" ");
                        var command = args[0];
                        var rest = args.length > 1
                            ? List.of(java.util.Arrays.copyOfRange(args, 1, args.length))
                            : List.<String>of();
                        builder.mcpServer(new CapabilityRegistry.McpServerConfig(name, command, rest, null));
                    } else {
                        builder.mcpServer(name, typeOrUrl);
                    }
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
