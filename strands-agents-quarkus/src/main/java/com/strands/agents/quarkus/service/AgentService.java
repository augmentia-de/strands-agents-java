package com.strands.agents.quarkus.service;

import com.strands.agents.core.*;
import com.strands.agents.core.model.agent.AgentPhase;
import com.strands.agents.core.model.agent.AgentResult;
import com.strands.agents.core.model.agent.ExecutionMetrics;
import com.strands.agents.core.model.agent.StopReason;
import com.strands.agents.core.model.event.*;
import com.strands.agents.quarkus.dto.ChatRequest;
import com.strands.agents.quarkus.dto.ChatResponse;
import com.strands.agents.quarkus.dto.SkillInfo;
import com.strands.agents.quarkus.dto.ToolInfo;
import com.strands.agents.sessions.FileSessionManager;
import com.strands.agents.skills.*;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class AgentService {

    private ToolRegistry fullRegistry;
    private List<Skill> allSkills;
    private ChatModel model;
    private FileSessionManager sessionManager;
    private Path logDir;
    private boolean llmLogEnabled;
    private Path skillsDir;
    private Path sessionDir;
    private String skillsDirProp;
    private String sessionDirProp;
    private boolean llmLogEnabledProp;
    private String llmLogPathProp;
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
    }

    public synchronized void ensureInitialized() {
        if (model != null) return;

        this.model = createModel();
        this.fullRegistry = createFullRegistry();
        this.allSkills = loadSkills();
        this.sessionManager = createSessionManager();
        this.logDir = Path.of(llmLogPathProp).getParent();

        setupLogging();
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

    public ChatResponse chat(ChatRequest req) {
        ensureInitialized();
        var start = System.nanoTime();

        var activeTools = filterTools(req);
        var activeSkills = filterSkills(req);
        var plugins = buildPlugins(activeSkills);
        var modelToUse = wrapModel(model);

        var agent = new StrandsAgent(modelToUse, activeTools, new ToolExecutor(),
            null, sessionManager, null, plugins);

        var phases = new CopyOnWriteArrayList<String>();
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "→" + sce.currentPhase());
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

    public void chatSse(ChatRequest req, java.util.function.Consumer<String> onToken,
                         java.util.function.Consumer<List<String>> onPhases,
                         java.util.function.Consumer<ChatResponse> onComplete) {
        ensureInitialized();
        var start = System.nanoTime();

        var activeTools = filterTools(req);
        var activeSkills = filterSkills(req);
        var plugins = buildPlugins(activeSkills);
        var modelToUse = wrapModel(model);

        var streamingModel = findStreamingModel();
        com.strands.agents.core.StreamingAgent agent;
        com.strands.agents.core.MockChatModel fallbackModel = null;
        if (streamingModel != null) {
            agent = new com.strands.agents.core.StreamingAgent(streamingModel,
                activeTools, new ToolExecutor(), null, sessionManager, null);
        } else {
            fallbackModel = new com.strands.agents.core.MockChatModel();
            agent = new com.strands.agents.core.StreamingAgent(
                new com.strands.agents.core.MockStreamingChatModel(),
                activeTools, new ToolExecutor());
        }

        var phases = new CopyOnWriteArrayList<String>();
        if (req.sessionId == null) {
            req.sessionId = UUID.randomUUID().toString();
        }

        agent.setEventListener(event -> {
            if (event instanceof AgentStateChangedEvent sce) {
                phases.add(sce.previousPhase() + "→" + sce.currentPhase());
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

    private List<Plugin> buildPlugins(List<Skill> skills) {
        var plugins = new ArrayList<Plugin>();
        if (!skills.isEmpty()) {
            plugins.add(new AgentSkillsPlugin(skills));
        }
        var hitlProvider = (HITLProvider) (action, context) -> {
            return ApprovalResult.approved(action);
        };
        plugins.add(new HITLPlugin(hitlProvider, HITLAuthority.AUTO));
        plugins.add(new GuardrailPlugin(List.of(), List.of()));
        return plugins;
    }

    private ChatModel createModel() {
        try {
            return com.strands.agents.core.ModelFactory.createOpenAiFromEnv();
        } catch (Exception e) {
            var mock = new com.strands.agents.core.MockChatModel();
            System.err.println("OPENAI_API_KEY nicht gesetzt – nutze MockChatModel");
            return mock;
        }
    }

    private dev.langchain4j.model.chat.StreamingChatModel findStreamingModel() {
        try {
            var apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey != null && !apiKey.isBlank()) {
                return dev.langchain4j.model.openai.OpenAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .build();
            }
        } catch (Exception e) {
            // fall through
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
            System.err.println("LLM-Logging nicht verfügbar: " + e.getMessage());
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
        return builder.build();
    }

    private List<Skill> loadSkills() {
        var dir = Path.of(skillsDirProp);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try {
            return SkillParser.fromDirectory(dir);
        } catch (Exception e) {
            System.err.println("Skills nicht ladbar: " + e.getMessage());
            return List.of();
        }
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
