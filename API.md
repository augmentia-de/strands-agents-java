# Strands Agents API Reference & Usage Guide

Core module `strands-agents` (v0.1.1-SNAPSHOT).  
All classes in package `de.augmentia.strandsagents` unless noted.  
Java 21 with `--enable-preview`.

---

## 1. Maven Coordinates

```xml
<!-- Core SDK (no framework dependency) -->
<dependency>
    <groupId>de.augmentia.strandsagents</groupId>
    <artifactId>strands-agents</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>

<!-- Quarkus REST API -->
<dependency>
    <groupId>de.augmentia.strandsagents</groupId>
    <artifactId>strands-agents-quarkus</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>

<!-- Examples -->
<dependency>
    <groupId>de.augmentia.strandsagents</groupId>
    <artifactId>strands-agents-examples</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

---

## 2. Agent

### 2.1 Agent

```java
// constructors — each adds configuration over the previous
Agent(ChatModel model)
Agent(ChatModel model, ToolRegistry registry, ToolExecutor executor)
Agent(ChatModel model, ToolRegistry registry, ToolExecutor executor, ConversationManager cm)
Agent(ChatModel model, ToolRegistry registry, ToolExecutor executor, ConversationManager cm,
      SessionManager sm, ResilienceConfig rc)
Agent(ChatModel model, ToolRegistry registry, ToolExecutor executor, ConversationManager cm,
      SessionManager sm, ResilienceConfig rc, List<Plugin> plugins)

// execution
AgentResult execute(String sessionId, String prompt)

AgentResult execute(String sessionId, String prompt, Map<String,Object> contextVariables)

// fluent builder-style setters (chainable, all return Agent)
Agent toolRegistry(ToolRegistry)
Agent sessionManager(SessionManager)
Agent conversationManager(ConversationManager)
Agent maxIterations(int)
Agent toolExecutor(ToolExecutor)
Agent hookRegistry(HookRegistry)
Agent plugin(Plugin)
Agent plugins(List<Plugin>)
Agent addEventListener(AgentEventListener)
Agent resilienceConfig(ResilienceConfig)
Agent structuredOutputConfig(StructuredOutputConfig)
Agent systemPrompt(String)
Agent agentName(String)
Agent skillsDir(Path)
Agent initialSkills(List<String>)
Agent tieredConfig(TieredModelConfig)
Agent modelTier(ModelTier)

// runtime tool/hook management (changes take effect on next LLM call)
Agent addTool(AgentTool<?> tool)
Agent addTool(Object annotatedInstance)   // @Tool annotated
Agent removeTool(String name)
Agent setToolRegistry(ToolRegistry)
Agent addHook(AgentHook hook)
Agent removeHook(String name)

// lifecycle
void pauseExecution()
void resumeExecution()

// event dispatch
void dispatchEvent(AgentEvent event)

// getters
ToolRegistry getToolRegistry()
HookRegistry getHookRegistry()
String getAgentName()
String getSystemPrompt()
ChatModel getChatModel()
int getMaxIterations()
AgentPhase getCurrentPhase()
boolean isPaused()
```

**Minimal usage:**

```java
var model = ModelFactory.createOpenAiFromEnv();
var agent = new Agent(model);
AgentResult result = agent.execute("What is the capital of France?");
System.out.println(result.finalAnswer());
```

**With mock LLM (no API key):**

```java
var agent = new Agent(new MockChatModel("Mock: %s"));
AgentResult result = agent.execute("Hello");
System.out.println(result.finalAnswer()); // "Mock: Hello"
```

**With tools, resilience, conversation, and plugins:**

```java
ConversationManager cm = new SlidingWindowConversationManager(10);
SessionManager sm = new FileSessionManager(Path.of(".sessions"));
ResilienceConfig rc = new ResilienceConfig(
    new RetryConfig(3, 1000, 2.0),
    new CircuitBreakerConfig(0.5f, 10L, 30L)
);
List<Plugin> plugins = List.of(
    new GuardrailPlugin(inputGuardrails, outputGuardrails),
    new HITLPlugin(hitlProvider, HITLAuthority.CONFIRM)
);

var agent = new Agent(model, registry, executor, cm, sm, rc, plugins);
agent.setSystemPrompt("You are a helpful and secure assistant.");
AgentResult result = agent.execute("List files in the current directory.");
```

**Context variables (available in hooks):**

```java
var vars = Map.of("userId", "123", "language", "de");
AgentResult result = agent.execute("Translate this", vars);
```

### 2.2 StreamingAgent

```java
class StreamingAgent extends Agent

StreamingAgent(StreamingChatModel streamingModel)
StreamingAgent(StreamingChatModel streamingModel, ChatModel syncFallback)

void executeStreaming(String sessionId, String prompt, Consumer<String> onToken)
void executeStreaming(String sessionId, String prompt, Map<String,Object> contextVariables,
                      Consumer<String> onToken)

Multi<AgentResult> executeStreamingReactive(String sessionId, String prompt)
```

**Usage:**

```java
var model = ModelFactory.createOpenAiFromEnv();
StreamingAgent agent = new StreamingAgent(model);
agent.executeStreaming("Tell me a story", token -> {
    System.out.print(token);
});
```

Internally wraps the `StreamingChatModel` in a `StreamingModelBridge` that accumulates tokens and fires `TokenEvent`.

### 2.3 RoutingAgent

```java
class RoutingAgent extends Agent

RoutingAgent(ChatModel simpleModel, ChatModel advancedModel,
             ToolRegistry toolRegistry, ToolExecutor toolExecutor,
             ConversationManager conversationManager, SessionManager sessionManager,
             ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
             List<Plugin> plugins)

ModelTier resolveRoutingTier(String userGoal)
ModelTier getResolvedTier()
void applyRouting()
```

The simple model classifies the user goal and switches to the advanced model if complex reasoning is detected.

### 2.4 PlanningAgent

```java
class PlanningAgent extends Agent

AgentResult executePlanned(String goal)
AgentResult resumeSession(String sessionId, String goal)

AgentPhase getPhase()
int getIterationCount()
int getRevisionCount()
List<String> getErrorLog()
Planner getPlanner()
```

Multi-step planning loop: plan → execute steps → revise → complete.

### 2.5 AgentFactory

```java
class AgentFactory

static ToolRegistry createToolRegistry(StrandsAgentConfig config)
static Agent createAgent(ChatModel model, StrandsAgentConfig config)
static Agent createAgent(ChatModel model, StrandsAgentConfig config, SessionManager sm)
static Agent createAgent(ChatModel model, StrandsAgentConfig config, List<Plugin> plugins, SessionManager sm)
static Agent createAgent(TieredModelConfig tieredConfig, StrandsAgentConfig config)
static RoutingAgent createRoutingAgent(TieredModelConfig tieredConfig, StrandsAgentConfig config)
static StreamingAgent createStreamingAgent(StreamingChatModel model, StrandsAgentConfig config)
```

### 2.6 AgentConfig (Builder)

```java
Agent agent = AgentConfig.builder()
    .name("my-agent")
    .modelName("gpt-4o")
    .systemPrompt("You are a helpful assistant.")
    .toolRegistry(toolRegistry)
    .maxIterations(15)
    .conversationManager(new SlidingWindowConversationManager(10))
    .sessionManager(new FileSessionManager(Path.of(".sessions")))
    .resilienceConfig(ResilienceConfig.DEFAULT)
    .plugins(List.of(guardrails, hitl, skillsPlugin))
    .skillsDir(Path.of("skills/"))
    .initialSkills(List.of("code-review"))
    .structuredOutputModel(MyRecord.class)
    .structuredOutputSchema("{\"type\": \"object\", ...}")
    .logLlmCalls(Path.of("logs/llm.json"))
    .build()
    .createAgent();

// Or pass an explicit model:
Agent agent = config.createAgent(myCustomModel);
```

---

## 3. Agent Loop Execution Flow

```
execute(prompt)
  │
  ├─ beforeAgent hook  ──── Modify→ new prompt | Cancel→ abort
  │
  ├─ [iteration loop, max 10]
  │   │
  │   ├─ ConversationManager.prune()
  │   ├─ Input guardrails ── block→ return FALLBACK/THROW/ESCALATE
  │   │
  │   ├─ Plugin hooks → skills XML injected into system prompt
  │   │
  │   ├─ beforeModelCall hook ──── Modify→ new tools | Cancel→ abort
  │   │                            (systemPrompt StringBuilder mutable in-place)
  │   │
  │   ├─ LLM call (CircuitBreaker → Retry → TokenRecovery)
  │   │
  │   ├─ afterModelCall hook ───── Modify→ new response | Retry→ redo | Cancel→ abort
  │   │
  │   ├─ Output guardrails ── block→ return FALLBACK/THROW/ESCALATE
  │   ├─ Structured output parsing ── fail→ force prompt retry
  │   ├─ If no tool calls → afterAgent hook ──── Modify→ new answer
  │   │                        → return COMPLETED
  │   │
  │   ├─ HITL (CONFIRM mode) ── deny→ return INTERRUPTED
  │   │
  │   └─ For each tool call:
  │       ├─ beforeToolCall hook ── Cancel→ skip this tool
  │       ├─ Execute tool (with retry)
  │       └─ afterToolCall hook ─── Modify→ new result
  │
  └─ [loop exhausted] → afterAgent hook → return MAX_ITERATIONS
```

Phase transitions: `IDLE → THINKING → OBSERVING → ACTING → ERROR → FINISHED`

**Stop reasons:** `COMPLETED`, `MAX_ITERATIONS`, `INTERRUPTED`, `ERROR`

**Async execution:**

```java
CompletableFuture<AgentResult> future = agent.executeAsync("Hello");
future.thenAccept(result -> System.out.println(result.finalAnswer()));
```

Uses virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).

---

## 4. Tools

### 4.1 AgentTool Interface

```java
interface AgentTool<P> {
    String name()
    String description()
    Class<P> parameterType()
    JsonNode parameterSchema()
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag,
                       Consumer<ToolResult> onUpdate) throws Exception
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag) throws Exception
}
```

### 4.2 @Tool Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Tool {
    String name() default "";
    String[] value() default {};
    String description() default "";
}
```

Annotated methods on a plain class are scanned via `ToolRegistry.register(Object)`.

### 4.3 ToolResult

```java
record ToolResult(List<ContentBlock> content, Object details)

static ToolResult success(String text)
static ToolResult success(String text, Object details)
static ToolResult error(String error)

// ContentBlock is a sealed interface:
sealed interface ContentBlock permits TextContent { ... }
record TextContent(String text) implements ContentBlock
```

### 4.4 ToolRegistry

```java
class ToolRegistry {
    void register(Object toolInstance)           // scans @Tool annotations
    void register(String name, Object instance, Method method)
    void register(String name, ToolSpecification spec, ToolMethod executor)
    void register(String name, ToolSpecification spec, ToolMethod executor, boolean force)
    void register(AgentTool<?> agentTool)
    void registerAll(Object... toolInstances)
    void unregister(String name)
    ToolMethod resolve(String name)
    boolean hasTool(String name)
    List<ToolSpecification> getSpecifications()
    Map<String, ToolMethod> getTools()
    Set<String> getToolNames()
    int size()

    // builder for common setups:
    Builder builder()
    @Deprecated Builder.standard(boolean bashAllowed, boolean httpAllowPrivate,
                                 Path workspace, String extraTools)
}
```

**Builder usage:**

```java
ToolRegistry registry = ToolRegistry.builder()
    .standard()                                // all 9 core file/web tools
    .with(new CalculatorTool())                // annotated @Tool instance
    .with("de.augmentia.strandsagents.core.tools.TimeTool")  // by class name
    .include("ReadTool", "WriteTool")          // whitelist
    .exclude("BashTool")                       // blacklist
    .cwd(Path.of("/tmp"))
    .build();
```

### 4.5 ToolExecutor

```java
class ToolExecutor {
    ToolExecutor()                                    // default 120s timeout
    ToolExecutor(long timeoutSeconds)
    ToolExecutionResult execute(ToolMethod method, String toolCallId, String jsonArgs,
                                AtomicBoolean abortFlag, Consumer<String> onToken)
    ToolExecutionResult execute(ToolMethod method, String toolCallId, String jsonArgs,
                                AtomicBoolean abortFlag)
}
```

Uses virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).
Supports `AbortFlag` for cooperative cancellation of long-running tools.

### 4.6 Built-in Tools

| `name()` | Description | Params | Package |
|---|---|---|---|
| `bash` | Execute bash commands (workspace-sandboxed) | `command: string, timeout: int?` | `local.BashTool` |
| `read` | Read file contents | `filePath: string, offset: int?, limit: int?` | `local.ReadTool` |
| `write` | Write content to file | `filePath: string, content: string` | `local.WriteTool` |
| `edit` | Edit file (replace text) | `filePath: string, oldString: string, newString: string` | `local.EditTool` |
| `grep` | Search file contents | `pattern: string, include: string?, path: string?` | `local.GrepTool` |
| `find` | Find files by glob pattern | `pattern: string, path: string?` | `local.FindTool` |
| `ls` | List directory contents | `path: string` | `local.LsTool` |
| `web_search` | Search the web (Tavily) | `query: string` | `local.WebSearchTool` |
| `web_fetch` | Fetch URL content | `url: string, format: string?` | `local.WebFetchTool` |
| `http` | HTTP GET/POST (private IP blocked by default) | `get(url)`, `post(url, jsonBody)` | `local.HttpTool` |
| `calculator` | Basic arithmetic | `add(a,b)`, `multiply(a,b)`, `stringLength(s)` | `tools.CalculatorTool` |
| `time` | Current date/time | `getCurrentTime()`, `getCurrentDate()` | `local.TimeTool` |
| `ask_user` | Human-in-the-loop prompt | `askUser(question)` | `tools.HumanInTheLoopTool` |
| `run` | Docker run (container sandbox) | `image: string, command: string?` | `tools.DockerRunTool` |
| `list_tools` | List available tools | `query: string?` | `tools.ListToolsTool` |
| `capability_search` | Analyze task, recommend matching capabilities (skills + tools) | `task: string` | `skills.CapabilitySearchTool` |
| `skill_search` | Search skills | `query: string` | `skills.SkillSearchTool` |
| `mcp_list` | List MCP server tools | — | `skills.McpListTool` |
| `mcp_ingest` | Ingest MCP tools as skills | — | `skills.McpIngestTool` |

Security: `WorkspacePaths` double-canonicalize check prevents symlink escape.
Chaos engineering: `RANDOM_TOOL_ERRORS_ENABLED` randomly injects timeouts, exceptions, or invalid JSON.

### 4.7 ToolCapability Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ToolCapability {
    CapabilityToken[] value() default {};
}
```

Marks a tool class with required security capability tokens (FILE_READ, FILE_WRITE, NETWORK, EXECUTE, etc.).

---

## 5. Hooks

Hooks intercept the agent at 6 lifecycle points.

### 5.1 AgentHook Interface

```java
interface AgentHook {
    String name()

    default HookResult beforeAgent(HookContexts.BeforeAgentContext ctx)
    default HookResult afterAgent(HookContexts.AfterAgentContext ctx, String response)
    default HookResult beforeModelCall(HookContexts.BeforeModelCallContext ctx)
    default HookResult afterModelCall(HookContexts.AfterModelCallContext ctx, String llmResponse)
    default HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx)
    default HookResult afterToolCall(HookContexts.AfterToolCallContext ctx, String toolResult)
}
```

### 5.2 HookResult

```java
sealed interface HookResult permits Continue, Cancel, Modify, Retry

record Continue()                    // proceed normally
record Cancel(String reason)         // abort with reason
record Modify<T>(T value)            // replace the value (prompt, tools, result)
record Retry(String reason)          // re-run the LLM call
```

**Where each result is supported:**

| Method | Continue | Cancel | Modify | Retry |
|--------|----------|--------|--------|-------|
| `beforeAgent` | ✓ | ✓ | Modify\<String\> (prompt) | — |
| `afterAgent` | ✓ | — | Modify\<String\> (response) | — |
| `beforeModelCall` | ✓ | ✓ | Modify\<List\<ToolSpecification\>\> (tools) | — |
| `afterModelCall` | ✓ | ✓ | Modify\<String\> (response) | ✓ |
| `beforeToolCall` | ✓ | ✓ | — | — |
| `afterToolCall` | ✓ | — | Modify\<String\> (result) | — |

### 5.3 HookContexts

```java
class HookContexts {
    record BeforeAgentContext(String sessionId, String prompt,
                              Map<String,Object> contextVariables)
    record AfterAgentContext(String sessionId, AgentResult result)
    record BeforeModelCallContext(String sessionId, StringBuilder systemPrompt,
                                  List<Message> messages, List<ToolSpecification> tools)
        // systemPrompt is mutable via StringBuilder
        // messages list is mutable — modify in place for PII masking
    record AfterModelCallContext(String sessionId, String llmResponse,
                                 int inputTokens, int outputTokens)
    record BeforeToolCallContext(String sessionId, String toolName,
                                 Map<String,Object> arguments)
    record AfterToolCallContext(String sessionId, String toolName,
                                String result, boolean isError)
}
```

### 5.4 HookRegistry

```java
class HookRegistry {
    void register(AgentHook hook)
    void register(HookProvider provider)
    void unregister(String name)
    void unregister(AgentHook hook)
    void clear()
    List<AgentHook> getHooks()
    void setFailurePolicy(HookFailurePolicy policy)
        // ISOLATE (default): failing hooks skipped, others continue
        // CHAIN_ABORT: any hook failure aborts entire chain

    // trigger methods — called by Agent internally:
    HookResult beforeAgent(HookContexts.BeforeAgentContext)
    HookResult afterAgent(HookContexts.AfterAgentContext, String)
    HookResult beforeModelCall(HookContexts.BeforeModelCallContext)
    HookResult afterModelCall(HookContexts.AfterModelCallContext, String)
    HookResult beforeToolCall(HookContexts.BeforeToolCallContext)
    HookResult afterToolCall(HookContexts.AfterToolCallContext, String)
}
```

### 5.5 HookProvider

```java
interface HookProvider {
    String name()
    void registerHooks(HookRegistry registry)
}
```

### 5.6 HookFailurePolicy

```java
enum HookFailurePolicy { ISOLATE, CHAIN_ABORT }
```

### 5.7 AgentEventListener

```java
@FunctionalInterface
interface AgentEventListener {
    void onEvent(AgentEvent event)
}
```

**Event types (sealed, 9 records):**

| Event | Fields |
|---|---|
| `AgentStartedEvent` | sessionId, timestamp, initialPrompt |
| `AgentFinishedEvent` | sessionId, timestamp, finalAnswer |
| `AgentStateChangedEvent` | sessionId, timestamp, prev Phase, curr Phase, goal, iterationCount, revisionCount |
| `BeforeInvocationEvent` | sessionId, timestamp, systemPrompt, currentMessages |
| `AfterInvocationEvent` | sessionId, timestamp, response, messages |
| `ModelRequestedEvent` | sessionId, timestamp, promptHistory |
| `TokenEvent` | sessionId, timestamp, token |
| `ToolExecutionStartedEvent` | sessionId, timestamp, toolCall |
| `ToolExecutionFinishedEvent` | sessionId, timestamp, result |

**Reactive streams:**

```java
Flow.Publisher<AgentEvent> stream = agent.eventStream();
stream.subscribe(new Flow.Subscriber<>() { ... });
```

### 5.8 Hook Examples

**Security guard — cancel dangerous tool calls:**

```java
AgentHook securityHook = new AgentHook() {
    @Override public String name() { return "security"; }

    @Override
    public HookResult beforeToolCall(BeforeToolCallContext ctx) {
        if (ctx.toolName().equals("BashTool")
                && ctx.arguments().get("command").toString().contains("rm -rf")) {
            return new HookResult.Cancel("Dangerous command blocked");
        }
        return new HookResult.Continue();
    }
};
```

**Modify system prompt per LLM call:**

```java
AgentHook languageHook = new AgentHook() {
    @Override public String name() { return "language"; }

    @Override
    public HookResult beforeModelCall(BeforeModelCallContext ctx) {
        ctx.systemPrompt().append("\nAnswer in German.");
        return new HookResult.Continue();
    }
};
```

**Retry LLM call on short response:**

```java
AgentHook retryOnShortAnswer = new AgentHook() {
    @Override public String name() { return "retry-short"; }

    @Override
    public HookResult afterModelCall(AfterModelCallContext ctx, String response) {
        if (response.length() < 10) {
            return new HookResult.Retry("Response too short, please elaborate");
        }
        return new HookResult.Continue();
    }
};
```

**Dynamic tool injection via hook:**

```java
AgentHook toolInjector = new AgentHook() {
    @Override public String name() { return "tool-injector"; }

    @Override
    public HookResult beforeModelCall(BeforeModelCallContext ctx) {
        var extended = new ArrayList<>(ctx.tools());
        if (ctx.messages().stream().anyMatch(m -> m.text().contains("calculate"))) {
            extended.add(ToolSpecification.builder()
                .name("special_calculator")
                .description("For complex calculations")
                .addParameter("expression", JsonSchemaProperty.STRING)
                .build());
        }
        return new HookResult.Modify<>(List.copyOf(extended));
    }
};
```

---

## 6. Plugins

### 6.1 Plugin Interface

```java
interface Plugin {
    String name()
    default void initAgent(Agent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
}
```

### 6.2 PluginRegistry

```java
class PluginRegistry implements AgentEventListener {
    PluginRegistry(List<Plugin> plugins)

    void initialize(Agent agent)
    void onEvent(AgentEvent event)
}
```

### 6.3 GuardrailPlugin

```java
class GuardrailPlugin implements Plugin

GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails,
                BlockAction blockAction)
GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails,
                BlockAction blockAction, String fallbackMessage)

// Guardrail is a functional interface:
interface Guardrail {
    GuardrailResult validate(List<Message> messages, String context)
}

record GuardrailResult(boolean pass, String reason, String sanitized) {
    static GuardrailResult ok()
    static GuardrailResult block(String reason)
    static GuardrailResult block(String reason, String sanitized)
}

enum BlockAction { THROW, FALLBACK, ESCALATE }
```

**Example:**

```java
Guardrail keywordFilter = (messages, context) -> {
    if (messages.stream().anyMatch(m -> m.text().contains("secret"))) {
        return GuardrailResult.failed("Contains forbidden keyword");
    }
    return GuardrailResult.ok();
};

GuardrailPlugin guardrails = new GuardrailPlugin(
    List.of(keywordFilter),
    List.of(),
    BlockAction.FALLBACK,
    "This content cannot be processed."
);
```

### 6.4 HITLPlugin

```java
class HITLPlugin implements Plugin

HITLPlugin(HITLProvider provider, HITLAuthority authority)

interface HITLProvider {
    ApprovalResult requestApproval(String action, String context)
}

enum HITLAuthority { AUTO, CONFIRM, REVIEW, DENY }

record ApprovalResult(String action, boolean approved, String feedback, Instant timestamp) {
    static ApprovalResult approved(String action)
    static ApprovalResult denied(String action, String feedback)
}
```

**Authority levels:**

| Value | Behavior |
|-------|----------|
| `AUTO` | Proceed without human intervention |
| `CONFIRM` | Require human confirmation before each tool execution |
| `REVIEW` | Execute but log for human review |
| `DENY` | Block all tool execution |

Built-in provider: `HITLHook.consoleProvider()` reads stdin for yes/no confirmation.

### 6.5 AgentSkillsPlugin

```java
class AgentSkillsPlugin implements Plugin

AgentSkillsPlugin(AgentSkillsConfig config)
AgentSkillsPlugin(List<Skill> skills, List<String> initialSkills)
```

Injects skill descriptions as XML into the system prompt before every LLM call. When `skillSearchEnabled`, the `SkillSearchTool` is also registered.

### 6.6 Checkpoints (HITL Checkpoint)

```java
class CheckpointHook implements AgentHook
interface CheckpointChannel {
    void notify(Checkpoint checkpoint)
}
class ConsoleChannel implements CheckpointChannel   // stdout
class SSEChannel implements CheckpointChannel        // SSE push

class CheckpointService {
    // manages PENDING / APPROVED / REJECTED checkpoints
}

record Checkpoint(String id, String sessionId, String action, String context,
                  Checkpoint.Status status, Instant createdAt)
```

### 6.7 Gate Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Gate {
    GateType type()                   // COOLDOWN, CRON, CONDITION, EVENT, MANUAL
    String duration() default ""
    String schedule() default ""
    String condition() default ""
    String on() default ""
}

enum GateType { COOLDOWN, CRON, CONDITION, EVENT, MANUAL }

interface GateEvaluator {
    boolean isOpen(Method pluginMethod, Gate gate)
    void recordExecution(Method pluginMethod, Gate gate, boolean success)
}
```

### 6.8 Combining Plugins

```java
var agent = new Agent(model, tools, executor, null, null, null,
    List.of(guardrails, hitl, skillsPlugin));
```

---

## 7. Conversation Management

```java
sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingConversationManager {
    List<Message> prune(List<Message> messages)
}

class SlidingWindowConversationManager implements ConversationManager {
    SlidingWindowConversationManager(int maxMessages, int maxTokens)
    // keeps last N messages, or truncates by token count
}

class SummarizingConversationManager implements ConversationManager {
    SummarizingConversationManager(ChatModel summaryModel, int maxMessages)
    // compresses old messages into summary when limit exceeded
}
```

Pass `null` for unbounded conversation history.

---

## 8. Session Management

```java
interface SessionManager {
    Session createSession(String agentName, Map<String,Object> metadata)
    Optional<Session> loadSession(String sessionId)
    void saveSession(Session session)
    void deleteSession(String sessionId)
    List<Session> listSessions(String agentName)
    List<Session> searchByMetadata(String key, String value)
}

record Session(String sessionId, String agentName, List<Message> messages, AgentState state,
               Map<String,Object> metadata, Instant createdAt, Instant updatedAt)

class FileSessionManager implements SessionManager {
    FileSessionManager(Path directory)
    // stores each session as {sessionId}.json with file locking
}

class JdbcSessionManager implements SessionManager {
    JdbcSessionManager(DataSource dataSource)
    // auto-creates sessions table with CLOB columns via MERGE statements
}
```

**Multi-turn usage:**

```java
var agent = new Agent(model, tools, executor, null, sm);

AgentResult r1 = agent.execute("session-1", "Hello");
AgentResult r2 = agent.execute("session-1", "What did I just say?");
// session is auto-persisted after each execute()
```

---

## 9. Multi-Tier LLM

```java
record TieredModelConfig(ChatModelConfig simple, ChatModelConfig advanced, ModelTier defaultTier)

record ChatModelConfig(ModelProviderType provider, String apiKey, String baseUrl,
                       String modelName, Double temperature, Integer maxRetries,
                       String ollamaBaseUrl, Boolean logRequests, Boolean logResponses)

enum ModelProviderType { OPENAI, OLLAMA, OPENAI_COMPATIBLE }
enum ModelTier { SIMPLE, ADVANCED, ROUTING }

class ModelFactory {
    static ChatModel createChatModel(ChatModelConfig config)
    static StreamingChatModel createStreamingChatModel(ChatModelConfig config)
}
```

**Model resolution order** for `baseUrl`/`apiKey`: System property `vault.<KEY>` → env var → system property.

**Fallback chain per tier:**
- SIMPLE: `SIMPLE_*` env vars → `OPENAI_*` → Vault → error
- ADVANCED: `ADVANCED_*` env vars → SIMPLE values → `OPENAI_*` → Vault → error

**Runtime model switching:**

```java
agent.switchTier(ModelTier.ADVANCED);
agent.setModelTier(ModelTier.SIMPLE);
agent.setAdvancedModel(newAdvancedModel);
agent.setSimpleModel(newSimpleModel);
```

**RoutingAgent:**

```java
RoutingAgent ra = new RoutingAgent(simpleModel, advancedModel, ...);
ra.resolveRoutingTier(userGoal);    // classifies goal as SIMPLE or ADVANCED
ra.applyRouting();                  // applies the resolved tier
```

---

## 10. Skills & Capabilities

### 10.1 Skill Model

```java
record Skill(String name, String description, String instructions, Path path,
             List<String> allowedTools, Map<String,Object> metadata,
             String license, String compatibility, List<String> declaredTools)
```

- `allowedTools` — from `allowed-tools:` frontmatter; controls auto-injection of skill instructions when these tools are activated
- `declaredTools` — from `tools:` frontmatter; tools the skill declares it uses/needs. **New:** used by `CapabilitySearchTool` for tool resolution and LLM enrichment.

### 10.2 SkillParser

```java
class SkillParser {
    static Skill fromContent(String content)
    static Skill fromFile(Path path)
    static List<Skill> fromDirectory(Path dir)
    static CompletableFuture<Skill> fromUrl(String url)
    static Path findSkillMdFile(Path dir)
}
```

Skills are markdown files with YAML frontmatter:

```yaml
---
name: code-review
description: Review code for bugs and style issues
allowedTools: [ReadTool, GrepTool]
tools: [find, grep, read]
---
Instructions for the agent...
```

### 10.3 CapabilityRegistry

```java
class CapabilityRegistry {
    CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers)

    List<Capability> discoverAll()
    List<Capability> discoverSkills()
    List<Capability> discoverTools()
    List<Skill> discoverAllSkills()        // full Skill objects from all dirs
    Skill getSkill(String name)             // look up a skill by name
    Set<String> knownToolNames()            // all registered default tool names

    record Capability(String name, String description, String source, CapabilityType type)
    enum CapabilityType { SKILL, MCP_TOOL, DEFAULT }

    static Builder builder()
    class Builder {
        Builder skillDir(Path dir)
        Builder mcpServer(String name, String url)
        Builder mcpServer(McpServerConfig config)
        Builder includeStandardTools(boolean include)
        Builder registerDefaultTool(String name, String description)
        CapabilityRegistry build()
    }
}
```

### 10.4 CapabilitySearchTool

```java
class CapabilitySearchTool implements AgentTool<CapabilitySearchTool.Params>

record Params(String task)
```

Creates a sub-agent (`CapabilitySearchAgent`) for LLM-driven runtime analysis of available capabilities.

**Output JSON (skill entry):**

```json
{
  "name": "java-coding-standards",
  "description": "...",
  "allowedTools": ["write", "read"],
  "declaredTools": ["find", "read", "grep"],
  "resolvedTools": ["find", "read", "grep", "ls"],
  "resolveSource": "llm_enriched",
  "unknownDeclared": []
}
```

| Field | Description |
|---|---|
| `declaredTools` | 1:1 from SKILL.md `tools:` frontmatter |
| `resolvedTools` | `declaredTools` + LLM enrichments (or `declaredTools` if unenriched) |
| `resolveSource` | `"skill_file"` (no enrichment) or `"llm_enriched"` |
| `unknownDeclared` | Tools in `declaredTools` not found in the registry |

### 10.5 CapabilitySearchAgent

```java
class CapabilitySearchAgent extends Agent

record Analysis(
    String analysis,
    List<String> recommendedSkills,
    List<String> recommendedTools,
    String reasoning,
    List<ToolEnrichment> toolEnrichments
)

record ToolEnrichment(String skillName, List<String> enrichedTools)
```

The sub-agent reviews each skill's `declaredTools` for completeness, suggests missing standard tools, flags typos, and explains reasoning in `analysis`.

### 10.6 SkillResolvedEvent

```java
record SkillResolvedEvent(
    String skillName,
    List<String> declaredTools,
    List<String> resolvedTools,
    String resolveSource,
    Instant timestamp
) {
    SkillResolvedEvent(String skillName, List<String> declaredTools,
                       List<String> resolvedTools, String resolveSource)
}
```

Fired after each skill match in `CapabilitySearchTool`. Consumers (e.g., agent loop) can listen and persist.

---

## 11. MCP (Model Context Protocol)

```java
class McpConnector {
    static McpClient connect(McpServerConfig config, ToolRegistry registry,
                             Set<String> selectedTools) throws Exception
    static List<ToolInfo> discoverTools(McpServerConfig config)
    static String prefix(McpServerConfig config)
}
```

**Transports:** SSE (Server-Sent Events), StreamableHTTP, stdio.

**Tool prefix:** `<serverName>_<toolName>` prevents name collisions.

**Configuration** (`config/MCP_SERVER_CONFIG.json`):

```json
{
  "mcpServers": {
    "filesystem": { "type": "sse", "url": "http://localhost:3000/sse" },
    "memory": { "type": "stdio", "command": "npx", "args": ["-y", "@modelcontextprotocol/server-memory"] }
  }
}
```

---

## 12. Security & Vault

### 12.1 CapabilityToken

```java
enum CapabilityToken {
    FILE_READ, FILE_WRITE, DB_READ, DB_WRITE, NETWORK, EXECUTE, LLM_CALL,
    S3_READ, S3_WRITE, KAFKA_PUBLISH, KAFKA_CONSUME, VAULT_READ, VAULT_WRITE
}
```

### 12.2 SecretProvider

```java
@FunctionalInterface
interface SecretProvider {
    String getSecret(String path, String key)
    default Map<String, String> getSecrets(String path)
}
```

Providers: HashiCorp Vault (KV v1/v2), AES-256/GCM encrypted key store (PBKDF2-100k), file, composite fallback chain.

---

## 13. Resilience

```java
record ResilienceConfig(RetryConfig retryConfig, CircuitBreakerConfig circuitBreakerConfig)

record RetryConfig(int maxAttempts, long backoffDelayMs, double backoffMultiplier) {
    static RetryConfig DEFAULT = new RetryConfig(3, 1000, 2.0)
}

record CircuitBreakerConfig(float failureRateThreshold, long slidingWindowSeconds,
                            long halfOpenDelaySeconds) {
    static CircuitBreakerConfig DEFAULT = new CircuitBreakerConfig(0.5f, 60, 30)
}
```

Pass `ResilienceConfig.NONE` or `null` to disable all resilience.

- **Retry:** Exponential backoff; skips 401/403 auth errors
- **CircuitBreaker:** CLOSED → OPEN → HALF_OPEN states
- **TokenRecovery:** Auto-truncates message history on context-length-exceeded and retries

---

## 14. Structured Output

```java
record StructuredOutputConfig(StructuredOutputMode mode, Class<?> outputClass,
                              String jsonSchema, String forcePrompt)

enum StructuredOutputMode { STATIC, DYNAMIC }
// STATIC: use jsonSchema + OpenAI response_format
// DYNAMIC: force-prompt fallback
```

**Usage:**

```java
agent.setStructuredOutputConfig(
    StructuredOutputConfig.staticModel(Person.class, "Output valid JSON only."));

AgentResult result = agent.execute("John is 30 years old.");
String json = result.structuredOutput();
// {"name":"John","age":30}
```

---

## 15. Planning / Chain-of-Thought

```java
interface Planner {
    Plan createPlan(String goal, List<String> availableToolNames)
    StepResult executeStep(Plan plan, int stepIndex, ToolExecutor executor, ToolRegistry registry)
    Plan revise(Plan plan, StepResult failure, String feedback)
    boolean isComplete(Plan plan, String finalOutput)
    int maxRevisions()
}

class CoTPlanner implements Planner {
    CoTPlanner(ChatModel model)
    CoTPlanner(ChatModel model, CheckpointStore checkpointStore)
}

record Plan(String goal, List<Step> steps, int currentStep, Map<String,Object> sharedContext)
record Step(String id, String description, String toolName, String argumentsTemplate,
            List<String> dependsOn, boolean optional)
record StepResult(boolean success, String output, String error, Map<String,Object> artifacts)
```

Up to 3 revision rounds on validation errors.

---

## 16. Telemetry

```java
class LoggingHook implements AgentEventListener {
    LoggingHook(String name)
}

class MetricsHook implements AgentEventListener {
    MetricsHook(MeterRegistry registry)
    // Micrometer counters: llmCalls, toolExecutions, errors
    // Micrometer timers: llmDuration, toolExecutionDuration, totalAgentDuration
}

class TracingHook implements AgentEventListener {
    // OpenTelemetry spans per agent session, LLM call, and tool execution
}

class FileLlmLogger {
    FileLlmLogger(Path path, int maxMegabytes, int maxBackups)
    // rolling file logger (default 2MB × 10 backups)
}

class LoggingChatModel implements ChatModel {
    LoggingChatModel(ChatModel delegate, FileLlmLogger logger)
    // wraps a ChatModel, logs every request/response
}
```

---

## 17. Message Model

```java
sealed interface Message
    permits UserMessage, AssistantMessage, SystemMessage, ToolMessage

record UserMessage(String id, Instant timestamp, String content, Map<String,Object> metadata)
record AssistantMessage(String id, Instant timestamp, String content, Map<String,Object> metadata,
                        List<ToolCall> toolCalls)
record SystemMessage(String id, Instant timestamp, String content, Map<String,Object> metadata)
record ToolMessage(String id, Instant timestamp, String content, Map<String,Object> metadata,
                   String toolCallId, String toolName)

record ToolCall(String id, String toolName, String arguments)
record ToolExecutionResult(String toolCallId, String toolName, String result, boolean isError)

class ChatMessageConverter {
    static List<Message> toDomainMessages(List<ChatMessage> langchain4j)
    static Message toDomainMessage(ChatMessage source)
    static List<ChatMessage> toLangChain4jMessages(List<Message> domain)
    static ChatMessage toLangChain4j(Message domain)
}
```

---

## 18. AgentResult

```java
record AgentResult(String sessionId, String finalAnswer, List<Message> generatedMessages,
                   ExecutionMetrics metrics, StopReason stopReason, String structuredOutput)

record ExecutionMetrics(long durationMs, int inputTokens, int outputTokens, int toolCallsCount)

enum StopReason { MAX_ITERATIONS, COMPLETED, INTERRUPTED, ERROR }
enum AgentPhase { IDLE, PLANNING, EXECUTING, REVIEWING, REVISING, COMPLETED, FAILED,
                  WAITING_FOR_HUMAN }
enum AgentStatus { IDLE, RUNNING, AWAITING_TOOL_EXECUTION, COMPLETED, FAILED, INTERRUPTED }
```

---

## 19. A2A (Agent-to-Agent Protocol)

```java
class SubAgentExecutor   // runs agents as sub-agents with virtual threads
class SubAgentTool implements AgentTool
    // delegates to another agent; recursion guard max 5 levels deep
class StrandsA2AProducers  // produces A2A-compatible messages
```

Configured via `AGENT_URL` env var (default `http://localhost:8080`).

**Multi-agent orchestration:**

```java
var specialist = new Agent(specialistModel, specialistTools, new ToolExecutor());
var coordinatorTools = ToolRegistry.builder()
    .standard(false).include("ReadTool", "WebSearchTool")
    .with(new SubAgentTool("code-specialist", specialist))
    .build();
```

---

## 20. AgentContext (ThreadLocal)

```java
class AgentContext {
    static AgentContext get()

    String sessionId()
    AgentPhase phase()
    Map<String,Object> variables()

    void setSessionId(String)
    void setPhase(AgentPhase)
    void setVariable(String, Object)
    void clear()
}
```

ThreadLocal holder accessible during agent execution. Cleared on completion.

---

## 21. Configuration Reference

### 21.1 StrandsAgentConfig

```java
record StrandsAgentConfig(
    String skillsDir,          // default "skills"
    String sessionDir,         // default ".sessions"
    boolean llmLogEnabled,     // default false
    String llmLogPath,         // default "logs/llm-calls.log"
    List<String> initialSkills,
    boolean skillSearchEnabled,// default false
    boolean mcpIngestEnabled,  // default false
    String mcpConfigPath,      // default "config/MCP_SERVER_CONFIG.json"
    String workspace,
    boolean bashAllowed,       // default false
    boolean httpAllowPrivate,  // default false
    String extraTools,
    String hitlTools,
    String hitlEmailRecipient
)
```

### 21.2 AgentConfig

```java
record AgentConfig(String name, String modelName, String systemPrompt, ToolRegistry toolRegistry,
                   int maxIterations, ConversationManager conversationManager,
                   SessionManager sessionManager, ChatMemoryStore chatMemoryStore,
                   ResilienceConfig resilienceConfig, List<Plugin> plugins,
                   Path skillsDir, List<String> initialSkills,
                   StructuredOutputConfig structuredOutputConfig, Path llmLogPath,
                   TieredModelConfig tieredConfig, ModelTier modelTier)
```

### 21.3 Environment Variables

| Variable | Description | Default |
|---|---|---|
| `OPENAI_API_KEY` | Single-provider API key | — |
| `OPENAI_BASE_URL` | Single-provider base URL | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | Single-provider model name | `gpt-4o` |
| `LLM_TEMPERATURE` | Model temperature | `0.7` |
| `LLM_MAX_RETRIES` | Max LLM retry attempts | `3` |
| `LLM_DEFAULT_TIER` | Default model tier | `simple` |
| `SIMPLE_PROVIDER` | Simple tier provider | `openai` |
| `SIMPLE_MODEL` | Simple tier model | `gpt-4o-mini` |
| `SIMPLE_API_KEY` | Simple tier API key | — |
| `SIMPLE_BASE_URL` | Simple tier base URL | — |
| `ADVANCED_PROVIDER` | Advanced tier provider | `openai` |
| `ADVANCED_MODEL` | Advanced tier model | `gpt-4o` |
| `ADVANCED_API_KEY` | Advanced tier API key | — |
| `ADVANCED_BASE_URL` | Advanced tier base URL | — |
| `STRANDS_SKILLS_DIR` | Skills directory | `skills` |
| `STRANDS_SESSION_DIR` | Session directory | `.sessions` |
| `STRANDS_LLM_LOG_ENABLED` | Enable LLM logging | `false` |
| `STRANDS_LLM_LOG_PATH` | LLM log file path | `logs/llm-calls.log` |
| `STRANDS_AGENT_TOOLS` | Extra tool FQCNs (comma-sep) | — |
| `STRANDS_AGENT_BASH_ALLOW` | Allow bash tool | `false` |
| `STRANDS_AGENT_HTTP_ALLOW_PRIVATE` | Allow HTTP to private IPs | `false` |
| `STRANDS_AGENT_WORKSPACE` | Workspace for file tools | — |
| `STRANDS_SKILLS_SEARCH` | Enable skill search | `false` |
| `STRANDS_MCP_CONFIG` | MCP config file path | `config/MCP_SERVER_CONFIG.json` |
| `STRANDS_MCP_INGEST` | Enable MCP ingest | `false` |
| `STRANDS_AGENT_HITL_TOOLS` | Tools requiring human approval | — |
| `TAVILY_API_KEY` | Tavily web search API key | — |
| `VAULT_ADDR` | HashiCorp Vault address | — |
| `VAULT_TOKEN` | HashiCorp Vault token | — |
| `VAULT_MOUNT_PATH` | Vault mount path | `secret` |
| `JSTRANDS_KEY_PATH` | AES key vault file path | `api-key.enc` |

---

## 22. Core API DTOs

```java
class ChatRequest {
    String prompt
    String sessionId
    List<String> tools
    List<String> skills
    String systemPrompt
}

class ChatResponse {
    String answer
    String sessionId
    StopReason stopReason
    long durationMs
    int inputTokens
    int outputTokens
    int toolCallsCount
    List<ToolCallInfo> toolCalls    // { name, arguments, result, durationMs, success }
    boolean memoryUsed
    List<String> memorySources
    List<String> phases
    String thinking
    String error
}

class AgentInitRequest {
    List<String> tools
    List<String> skills
    List<String> initialSkills
    String mcpServerName
    List<String> mcpTools
    String systemPrompt
    List<McpServerSelection> mcpServers
    Boolean skillSearchEnabled
    Boolean mcpIngestEnabled
    String capabilityDirs
    String sessionId
    String modelTier               // "simple", "advanced", "routing"
    String simpleProvider
    String advancedProvider
    String simpleModel
    String advancedModel
}

class ToolInfo {
    String name
    String description
    String parameters               // JSON schema as string
}

class SkillInfo {
    String name
    String description
}
```

---

## 23. Quarkus REST API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/api/chat` | POST | Send prompt, get response |
| `/api/chat/stream` | POST (SSE) | Streaming response via SSE |
| `/api/agent/init` | POST | Init agent session with tool/skill selection |
| `/api/agent/release` | POST | Release session |
| `/api/tools` | GET | List registered tools |
| `/api/skills` | GET | List loaded skills |
| `/api/mcp/discover` | POST | Discover MCP server tools |
| `/api/mcp/connect` | POST | Connect custom MCP server |
| `/api/mcp/servers` | GET | List configured MCP servers |
| `/api/sessions` | GET | List active sessions |
| `/api/admin/setup` | POST | Save API key + password (AES vault) |
| `/api/admin/activate` | POST | Activate API key |
| `/api/vault/status` | GET | Key vault status |
| `/api/vault/write` | POST | Save/delete key |
| `/q/swagger-ui` | GET | Swagger UI |
| `/q/health` | GET | Health check |

---

## 24. Prompts

```java
interface PromptManager {
    String get(String key, Object... args)
    default String getOrDefault(String key, String fallback, Object... args)
}

class PromptRegistry {
    static String get(String key, Object... args)
    // 29 keys defined in prompts.yaml
}

class YamlPromptManager implements PromptManager {
    YamlPromptManager(String... classpathResources)
    YamlPromptManager(Path overrideDir, String... classpathResources)
}

class CompositePromptManager implements PromptManager {
    CompositePromptManager(PromptManager... chain)
}
```

Override directory: set `strands.agent.prompts.override-dir` system property.

---

## 25. Complete Examples

### 25.1 Minimal File Editor Agent

```java
var model = ModelFactory.createOpenAiFromEnv();

var tools = ToolRegistry.builder()
    .standard()
    .exclude("BashTool", "WebFetchTool", "WebSearchTool")
    .build();

var agent = new Agent(model, tools, new ToolExecutor());
agent.setSystemPrompt("You are a file editing assistant. Read, write, and edit files.");

AgentResult result = agent.execute("Read src/main.java, find the bug, and fix it.");
System.out.println(result.finalAnswer());
```

### 25.2 Research Agent with Session and Resilience

```java
var model = ModelFactory.createOpenAiFromEnv();
var tools = ToolRegistry.builder().standard().with(new CalculatorTool()).build();
var conv = new SlidingWindowConversationManager(20);
var session = new FileSessionManager(Path.of(".research-sessions"));
var resilience = ResilienceConfig.DEFAULT;

var agent = new Agent(model, tools, new ToolExecutor(), conv, session, resilience);
agent.setSystemPrompt("You are a research assistant. Use web search and fetch to answer.");

agent.execute("research-1", "Find the latest Java version");
agent.execute("research-1", "What are its key features?");
agent.execute("research-1", "Summarize everything so far");
```

### 25.3 Guarded Agent with HITL, Hooks, and Streaming

```java
Guardrail keywordGuard = (msgs, ctx) -> msgs.stream()
    .anyMatch(m -> m.text().contains("password"))
    ? GuardrailResult.failed("password detected")
    : GuardrailResult.ok();

var guardrails = new GuardrailPlugin(
    List.of(keywordGuard), List.of(), BlockAction.FALLBACK, "Sensitive content blocked.");
var hitl = new HITLPlugin(HITLHook.consoleProvider(), HITLAuthority.CONFIRM);

AgentHook logger = new AgentHook() {
    @Override public String name() { return "timing"; }
    @Override public HookResult afterModelCall(AfterModelCallContext ctx, String response) {
        System.out.println("LLM call: " + ctx.inputTokens() + " in, " + ctx.outputTokens() + " out");
        return new HookResult.Continue();
    }
    @Override public HookResult beforeToolCall(BeforeToolCallContext ctx) {
        System.out.println("Executing: " + ctx.toolName());
        return new HookResult.Continue();
    }
};

var hooks = new HookRegistry();
hooks.register(logger);

var agent = new Agent(model, tools, new ToolExecutor(),
    null, null, null, List.of(guardrails, hitl), hooks);
agent.setSystemPrompt("You are a guarded assistant.");

AgentResult result = agent.execute("What is my password?");
System.out.println(result.finalAnswer());  // "Sensitive content blocked."
```

### 25.4 Structured Extraction Agent

```java
record Person(String name, int age, List<String> hobbies) {}

var agent = new Agent(ModelFactory.createOpenAiFromEnv());
agent.setSystemPrompt("Extract structured information from text.");
agent.setStructuredOutputConfig(
    StructuredOutputConfig.staticModel(Person.class, "Output valid JSON only."));

AgentResult result = agent.execute("John is 30 years old and enjoys hiking, reading, and coding.");
String json = result.structuredOutput();
// {"name":"John","age":30,"hobbies":["hiking","reading","coding"]}
```

### 25.5 Full Configuration via AgentConfig Builder

```java
var agent = AgentConfig.builder()
    .name("production-agent")
    .modelName("gpt-4o-mini")
    .systemPrompt("You are a production assistant.")
    .toolRegistry(ToolRegistry.builder().standard().build())
    .maxIterations(20)
    .conversationManager(new SummarizingConversationManager(
        ModelFactory.createOpenAiFromEnv(), 4096))
    .sessionManager(new FileSessionManager(Path.of(".sessions")))
    .resilienceConfig(ResilienceConfig.DEFAULT)
    .plugins(List.of(
        new GuardrailPlugin(List.of(), List.of(), BlockAction.FALLBACK, "Blocked"),
        new HITLPlugin(HITLHook.consoleProvider(), HITLAuthority.AUTO)))
    .structuredOutputModel(ExtractionResult.class)
    .logLlmCalls(Path.of("logs/llm.json"))
    .build()
    .createAgent();
```

### 25.6 Multi-Agent with Skills

```java
var codeReviewSkill = new Skill("code-review", "Review Java code for best practices",
    "Check for: null safety, exception handling, thread safety", ...);

var skillsPlugin = new AgentSkillsPlugin(List.of(codeReviewSkill), List.of("code-review"));

var tools = ToolRegistry.builder().standard().exclude("WebSearchTool", "WebFetchTool").build();
var agent = new Agent(model, tools, new ToolExecutor(), null, null, null, List.of(skillsPlugin));
agent.setSystemPrompt("You are a code quality assistant.");
agent.execute("Review the file src/com/example/Service.java");
```

### 25.7 Guarded Agent Production Pattern

```java
GuardrailPlugin guardrails = new GuardrailPlugin(
    List.of(new PiiGuardrail(), new PromptInjectionGuardrail()),
    List.of(new SensitiveDataGuardrail()),
    BlockAction.FALLBACK, "Inhalt konnte nicht verarbeitet werden.");

HITLPlugin hitl = new HITLPlugin(new ConsoleHITLProvider(), HITLAuthority.CONFIRM);

var hooks = new HookRegistry();
hooks.register(new AuditLogHook());
hooks.register(new RateLimitHook());

var agent = new Agent(model, tools, new ToolExecutor(),
    new SlidingWindowConversationManager(20),
    new FileSessionManager(Path.of(".sessions")),
    ResilienceConfig.DEFAULT,
    List.of(guardrails, hitl), hooks);
```

### 25.8 Coding Agent Workflow

```java
var tools = ToolRegistry.builder()
    .standard(false)
    .with(new HttpTool())
    .exclude("WebSearchTool")
    .build();

agent.setSystemPrompt("You are a coding agent. Read files, understand the codebase, "
    + "then make targeted edits. Verify your changes by reading the edited files.");
```

### 25.9 Capability-Driven Dynamic Workflow

```java
var capRegistry = CapabilityRegistry.builder()
    .skillDir(Path.of("skills/"))
    .mcpServer("db", "http://localhost:3001/sse")
    .build();

var tools = ToolRegistry.builder()
    .standard(false).exclude("BashTool")
    .with(new CapabilitySearchTool(capRegistry, model))
    .build();

agent.setSystemPrompt("Use capability_search to discover tools and skills "
    + "relevant to your task before starting.");
```

---

## 26. Workflow / Orchestration

```java
enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, WAITING_FOR_HUMAN }
```

- **WorkCoordinator:** Dispatch + Collect pattern
- **WorkflowDefinition:** Steps with roles, input/output mapping, next-step logic

---

## 27. Runtime Switching Summary

| Component | Runtime switch | Methods | Effect |
|---|---|---|---|
| Tools | ✓ | `addTool`, `removeTool`, `setToolRegistry` | Next LLM call |
| Hooks | ✓ | `addHook`, `removeHook`, `setHookRegistry` | Next iteration |
| System Prompt | ✓ | `setSystemPrompt` | Next LLM call |
| Model (Tier) | ✓ | `switchTier`, `setModelTier` | Next LLM call |
| Plugins | ✗ | Constructor only | — |
| Skills | ⚠️ | `AgentSkillsPlugin.activateSkill()` | Next prompt injection |
| ConversationManager | ✗ | Constructor only | — |
| SessionManager | ✗ | Constructor only | — |
