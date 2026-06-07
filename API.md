# Strands Agents API Reference

SDK documentation for the `strands-agents` core module (v0.1.1-SNAPSHOT).  
All classes are in package `de.augmentia.strandsagents` unless noted.  
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

<!-- Quarkus REST API module -->
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
    // run the agent loop on a prompt; returns final answer + metadata

AgentResult execute(String sessionId, String prompt, Map<String,Object> contextVariables)
    // same, with additional context variables available to hooks/plugins

// fluent builder-style setters
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

### 2.2 StreamingAgent

```java
class StreamingAgent extends Agent

StreamingAgent(StreamingChatModel streamingModel)
StreamingAgent(StreamingChatModel streamingModel, ChatModel syncFallback)
    // fallback ChatModel for tool execution and non-streaming parts

void executeStreaming(String sessionId, String prompt, Consumer<String> onToken)
void executeStreaming(String sessionId, String prompt, Map<String,Object> contextVariables,
                      Consumer<String> onToken)
    // emits tokens via onToken callback; final AgentResult is stored internally

Multi<AgentResult> executeStreamingReactive(String sessionId, String prompt)
    // returns Mutiny Multi emitting chunks, then the final AgentResult
```

### 2.3 RoutingAgent

```java
class RoutingAgent extends Agent

RoutingAgent(ChatModel simpleModel, ChatModel advancedModel,
             ToolRegistry toolRegistry, ToolExecutor toolExecutor,
             ConversationManager conversationManager, SessionManager sessionManager,
             ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
             List<Plugin> plugins)
RoutingAgent(ChatModel simpleModel, ChatModel advancedModel, LlmRouter router,
             ToolRegistry toolRegistry, ToolExecutor toolExecutor,
             ConversationManager conversationManager, SessionManager sessionManager,
             ChatMemoryStore chatMemoryStore, ResilienceConfig resilienceConfig,
             List<Plugin> plugins)
    // auto-selects simple or advanced model per request

ModelTier resolveRoutingTier(String userGoal)
    // classifies the user goal → SIMPLE or ADVANCED

ModelTier getResolvedTier()
    // the tier chosen for the current execution

void applyRouting()
    // apply routing before execute()
```

### 2.4 PlanningAgent

```java
class PlanningAgent extends Agent

// 6 constructors, progressively adding Planner, ToolRegistry, CheckpointStore, etc.

AgentResult executePlanned(String goal)
    // multi-step planning loop: plan → execute steps → revise → complete

AgentResult resumeSession(String sessionId, String goal)
    // resume from last completed checkpoint step

// introspection
AgentPhase getPhase()
int getIterationCount()
int getRevisionCount()
List<String> getErrorLog()
Planner getPlanner()
```

### 2.5 AgentFactory

```java
class AgentFactory

static ToolRegistry createToolRegistry(StrandsAgentConfig config)
    // builds a tool registry from config (bash, http, extraTools, workspace)

static Agent createAgent(ChatModel model, StrandsAgentConfig config)
static Agent createAgent(ChatModel model, StrandsAgentConfig config, SessionManager sm)
static Agent createAgent(ChatModel model, StrandsAgentConfig config, List<Plugin> plugins,
                         SessionManager sm)
static Agent createAgent(TieredModelConfig tieredConfig, StrandsAgentConfig config)
static RoutingAgent createRoutingAgent(TieredModelConfig tieredConfig, StrandsAgentConfig config)
static StreamingAgent createStreamingAgent(StreamingChatModel model, StrandsAgentConfig config)
```

---

## 3. Tools

### 3.1 AgentTool Interface

```java
interface AgentTool<P> {
    String name()                              // unique tool name
    String description()                       // LLM-facing description
    Class<P> parameterType()                   // typed parameter record class
    JsonNode parameterSchema()                 // JSON schema for parameters
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag,
                       Consumer<ToolResult> onUpdate) throws Exception
    ToolResult execute(String toolCallId, P params, AtomicBoolean abortFlag)
        throws Exception                       // convenience, no onUpdate
}
```

### 3.2 @Tool Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Tool {
    String name() default "";
    String[] value() default {};
    String description() default "";
}
```
Example: annotated methods on a plain class are scanned via `ToolRegistry.register(Object)`.

### 3.3 ToolResult

```java
record ToolResult(List<ContentBlock> content, Object details)

static ToolResult success(String text)
static ToolResult success(String text, Object details)
static ToolResult error(String error)

// ContentBlock is a sealed interface:
sealed interface ContentBlock permits TextContent { ... }
record TextContent(String text) implements ContentBlock
```

### 3.4 ToolRegistry

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

    // inner interfaces:
    interface ToolMethod {
        ToolSpecification spec()
        String execute(String jsonArguments) throws Exception
    }

    // builder for common setups:
    Builder builder()
    @Deprecated Builder.standard(boolean bashAllowed, boolean httpAllowPrivate,
                                 Path workspace, String extraTools)
}
```

### 3.5 ToolExecutor

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

### 3.6 Built-in Tools (AgentTool implementations)

| `name()` | `description` | params | package |
|---|---|---|---|
| `bash` | Execute bash commands | `command: string, timeout: int?` | `local.BashTool` |
| `read` | Read file contents | `filePath: string, offset: int?, limit: int?` | `local.ReadTool` |
| `write` | Write content to file | `filePath: string, content: string` | `local.WriteTool` |
| `edit` | Edit file (replace text) | `filePath: string, oldString: string, newString: string` | `local.EditTool` |
| `grep` | Search file contents | `pattern: string, include: string?, path: string?` | `local.GrepTool` |
| `find` | Find files by glob pattern | `pattern: string, path: string?` | `local.FindTool` |
| `ls` | List directory contents | `path: string` | `local.LsTool` |
| `web_search` | Search the web (Tavily) | `query: string` | `local.WebSearchTool` |
| `web_fetch` | Fetch URL content | `url: string, format: string?` | `local.WebFetchTool` |
| `http` | HTTP GET/POST | `get(url)`, `post(url, jsonBody)` | `local.HttpTool` |
| `calculator` | Basic arithmetic | `add(a,b)`, `multiply(a,b)`, `stringLength(s)` | `tools.CalculatorTool` |
| `time` | Current date/time | `getCurrentTime()`, `getCurrentDate()` | `local.TimeTool` |
| `ask_user` | Human-in-the-loop prompt | `askUser(question)` | `tools.HumanInTheLoopTool` |
| `run` | Docker run | `image: string, command: string?` | `tools.DockerRunTool` |
| `list_tools` | List available tools | `query: string?` | `tools.ListToolsTool` |
| `capability_search` | Search capabilities | `query: string` | `skills.CapabilitySearchTool` |
| `skill_search` | Search skills | `query: string` | `skills.SkillSearchTool` |
| `mcp_list` | List MCP server tools | — | `skills.McpListTool` |
| `mcp_ingest` | Ingest MCP tools as skills | — | `skills.McpIngestTool` |

### 3.7 ToolCapability Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ToolCapability {
    CapabilityToken[] value() default {};
}
```
Marks a tool class with required security capabilities.

---

## 4. Hooks

### 4.1 AgentHook Interface

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

### 4.2 HookResult

```java
sealed interface HookResult
    permits Continue, Cancel, Modify, Retry

record Continue() implements HookResult
    // proceed normally — no changes

record Cancel(String reason) implements HookResult
    // abort the current agent operation with given reason

record Modify<T>(T value) implements HookResult
    // replace the current value (e.g. modified tool arguments, tools list)

record Retry(String reason) implements HookResult
    // request retry of the current phase (LLM call or tool execution)
```

### 4.3 HookContexts

```java
class HookContexts {
    record BeforeAgentContext(String sessionId, String prompt,
                              Map<String,Object> contextVariables)
    record AfterAgentContext(String sessionId, AgentResult result)
    record BeforeModelCallContext(String sessionId, StringBuilder systemPrompt,
                                  List<Message> messages, List<ToolSpecification> tools)
        // messages list is mutable — modify in place for PII masking etc.
        // systemPrompt is mutable via StringBuilder
    record AfterModelCallContext(String sessionId, String llmResponse,
                                 int inputTokens, int outputTokens)
    record BeforeToolCallContext(String sessionId, String toolName,
                                 Map<String,Object> arguments)
    record AfterToolCallContext(String sessionId, String toolName,
                                String result, boolean isError)
}
```

### 4.4 HookRegistry

```java
class HookRegistry {
    void register(AgentHook hook)
    void register(HookProvider provider)     // bulk registration
    void unregister(String name)
    void unregister(AgentHook hook)
    void clear()
    List<AgentHook> getHooks()
    void setFailurePolicy(HookFailurePolicy policy)
        // ISOLATE: failing hooks are skipped, others continue
        // CHAIN_ABORT: any hook failure aborts the entire chain

    // trigger methods — called by Agent internally:
    HookResult beforeAgent(HookContexts.BeforeAgentContext)
    HookResult afterAgent(HookContexts.AfterAgentContext, String)
    HookResult beforeModelCall(HookContexts.BeforeModelCallContext)
    HookResult afterModelCall(HookContexts.AfterModelCallContext, String)
    HookResult beforeToolCall(HookContexts.BeforeToolCallContext)
    HookResult afterToolCall(HookContexts.AfterToolCallContext, String)
}
```

### 4.5 HookProvider

```java
interface HookProvider {
    String name()
    void registerHooks(HookRegistry registry)
}
```

### 4.6 HookFailurePolicy

```java
enum HookFailurePolicy { ISOLATE, CHAIN_ABORT }
```

### 4.7 AgentEventListener

```java
@FunctionalInterface
interface AgentEventListener {
    void onEvent(AgentEvent event)
}
```
Subtypes of `AgentEvent` (sealed, 9 records):

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

---

## 5. Plugins

### 5.1 Plugin Interface

```java
interface Plugin {
    String name()
    default void initAgent(Agent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
}
```

### 5.2 PluginRegistry

```java
class PluginRegistry implements AgentEventListener {
    PluginRegistry(List<Plugin> plugins)

    @FunctionalInterface
    interface BeforeInvocationHook {
        void onBeforeInvocation(BeforeInvocationEvent event)
    }

    void registerBeforeInvocationHook(BeforeInvocationHook hook)
    void initialize(Agent agent)    // calls plugin.initAgent() for each plugin
    void onEvent(AgentEvent event)  // forwards events to all plugins
}
```

### 5.3 Built-in Plugins

```java
class GuardrailPlugin implements Plugin
    // registers GuardrailHook in initAgent()
    GuardrailPlugin(List<Guardrail> inputGuardrails, List<Guardrail> outputGuardrails,
                    BlockAction blockAction)

class HITLPlugin implements Plugin
    // registers HITLHook in initAgent()
    HITLPlugin(HITLProvider provider, HITLAuthority authority)

class AgentSkillsPlugin implements Plugin
    // injects skill instructions into the system prompt
    AgentSkillsPlugin(AgentSkillsConfig config)
```

### 5.4 Guardrail Interface

```java
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

### 5.5 HITL Interface

```java
interface HITLProvider {
    ApprovalResult requestApproval(String action, String context)
}

enum HITLAuthority { AUTO, CONFIRM, REVIEW, DENY }

record ApprovalResult(String action, boolean approved, String feedback, Instant timestamp) {
    static ApprovalResult approved(String action)
    static ApprovalResult denied(String action, String feedback)
}
```

### 5.6 Checkpoint / HITL Checkpoint

```java
class CheckpointHook implements AgentHook
    // intercepts before/after tool call, creates checkpoints for approval

interface CheckpointChannel {
    void notify(Checkpoint checkpoint)
}

class ConsoleChannel implements CheckpointChannel    // stdout-based
class SSEChannel implements CheckpointChannel         // SSE push-based

class CheckpointService {
    // manages pending/approved/rejected checkpoints
}

record Checkpoint(String id, String sessionId, String action, String context,
                  Checkpoint.Status status, Instant createdAt)
    // Status: PENDING, APPROVED, REJECTED
```

### 5.7 Gate Annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Gate {
    GateType type()                   // COOLDOWN, CRON, CONDITION, EVENT, MANUAL
    String duration() default ""      // e.g. "30s", "5m"
    String schedule() default ""      // cron expression
    String condition() default ""     // SpEL condition
    String on() default ""            // event name
}

enum GateType { COOLDOWN, CRON, CONDITION, EVENT, MANUAL }

interface GateEvaluator {
    boolean isOpen(Method pluginMethod, Gate gate)
    void recordExecution(Method pluginMethod, Gate gate, boolean success)
}
```

---

## 6. Prompts

```java
interface PromptManager {
    String get(String key, Object... args)
    default String getOrDefault(String key, String fallback, Object... args)
}

class PromptRegistry {
    static String get(String key, Object... args)
        // static facade over the configured CompositePromptManager
        // 29 keys defined in prompts.yaml (e.g. "agent.initial_prompt", "agent.hook_cancelled")
}

class YamlPromptManager implements PromptManager {
    YamlPromptManager(String... classpathResources)
    YamlPromptManager(Path overrideDir, String... classpathResources)
}

class CompositePromptManager implements PromptManager {
    CompositePromptManager(PromptManager... chain)
        // first-match-wins: tries managers in order, returns first non-null result
}
```
Override directory: set `strands.agent.prompts.override-dir` system property to a path containing YAML files with the same keys.

---

## 7. Session Management

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
    // stores each session as a JSON file: {sessionId}.json
}

class JdbcSessionManager implements SessionManager {
    JdbcSessionManager(DataSource dataSource)
    // auto-creates sessions table with CLOB columns
}

class FileChatMemoryStore implements ChatMemoryStore {
    FileChatMemoryStore(Path directory)
}

class InMemoryChatMemoryStore implements ChatMemoryStore {
    // ConcurrentHashMap-based; non-persistent
}
```

---

## 8. Conversation Management

```java
sealed interface ConversationManager
    permits SlidingWindowConversationManager, SummarizingConversationManager {
    List<Message> prune(List<Message> messages)
}

class SlidingWindowConversationManager implements ConversationManager {
    SlidingWindowConversationManager(int maxMessages, int maxTokens)
    // keeps the last N messages, or truncates by token count
}

class SummarizingConversationManager implements ConversationManager {
    SummarizingConversationManager(ChatModel summaryModel, int maxMessages)
    // compresses old messages into a summary when limit is exceeded
}
```

---

## 9. Multi-Tier LLM

```java
record TieredModelConfig(ChatModelConfig simple, ChatModelConfig advanced, ModelTier defaultTier)

record ChatModelConfig(ModelProviderType provider, String apiKey, String baseUrl, String modelName,
                       Double temperature, Integer maxRetries, String ollamaBaseUrl)

enum ModelProviderType { OPENAI, OLLAMA, OPENAI_COMPATIBLE }
enum ModelTier { SIMPLE, ADVANCED, ROUTING }

class ModelFactory {
    static ChatModel createChatModel(ChatModelConfig config)
    static StreamingChatModel createStreamingChatModel(ChatModelConfig config)
}

record LlmConfig(String apiKey, String baseUrl, String modelName, Double temperature,
                 Integer maxRetries)
```

Model resolution order for `baseUrl`/`apiKey`: System property `vault.<KEY>` → env var → system property.

---

## 10. Skills & Capabilities

```java
record Skill(String name, String description, String instructions, Path path,
             List<String> allowedTools, Map<String,Object> metadata,
             String license, String compatibility)

class SkillParser {
    static Skill parse(Path file) throws IOException
    static List<Skill> parseDirectory(Path dir) throws IOException
}

class AgentSkillsPlugin implements Plugin {
    AgentSkillsPlugin(AgentSkillsConfig config)
}

class CapabilityRegistry {
    CapabilityRegistry(List<Path> skillDirectories, List<McpServerConfig> mcpServers)
    List<Capability> discoverAll()
    List<Capability> search(String query)
    McpServerConfig getServer(String name)

    // inner records:
    record Capability(String name, String description, CapabilityType type, Path path)
    enum CapabilityType { SKILL, MCP_TOOL }
    record McpServerConfig(String name, String url, TransportType transport,
                           String command, List<String> args)
    enum TransportType { SSE, STREAMABLE_HTTP }

    // builder:
    static Builder builder()
    class Builder {
        Builder skillDir(Path dir)
        Builder mcpServer(String name, String url)
        Builder mcpServer(McpServerConfig config)
        CapabilityRegistry build()
    }
}
```

---

## 11. MCP (Model Context Protocol)

```java
class McpConnector {
    static McpClient connect(McpServerConfig config, ToolRegistry registry,
                             Set<String> selectedTools) throws Exception
        // connects to MCP server and registers selected tools with namespace prefix

    static List<ToolInfo> discoverTools(McpServerConfig config)
        // discovers all available tools on a server without connecting

    static String prefix(McpServerConfig config)
        // returns the namespace prefix "serverName_" for tool name isolation
}
```
MCP servers are configured in a JSON file (default `config/MCP_SERVER_CONFIG.json`):
```json
{
  "mcpServers": {
    "filesystem": { "type": "sse", "url": "http://localhost:3000/sse" },
    "memory": { "type": "stdio", "command": "npx", "args": ["-y", "@modelcontextprotocol/server-memory"] }
  }
}
```

---

## 12. Security

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
        // throws UnsupportedOperationException by default
}
```

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
Skips retry for 401/403 responses (auth errors). Uses exponential backoff.

---

## 14. Structured Output

```java
record StructuredOutputConfig(StructuredOutputMode mode, Class<?> outputClass,
                              String jsonSchema, String forcePrompt)

enum StructuredOutputMode { STATIC, DYNAMIC }
    // STATIC: use jsonSchema + OpenAI response_format
    // DYNAMIC: inject instruction into system prompt (force-prompt fallback)
```

---

## 15. Planning / CoT

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

record Plan(String goal, List<Step> steps, int currentStep, Map<String,Object> sharedContext) {
    Plan withStep(int index)
    Plan advanceStep()
    Plan withSharedContext(Map<String,Object> context)
    boolean isComplete()
    Step current()
}

record Step(String id, String description, String toolName, String argumentsTemplate,
            List<String> dependsOn, boolean optional)

record StepResult(boolean success, String output, String error, Map<String,Object> artifacts) {
    static StepResult ok(String output)
    static StepResult ok(String output, Map<String,Object> artifacts)
    static StepResult fail(String error)
}

interface CheckpointStore {
    void saveStepStatus(String sessionId, String stepId, StepStatus status, String output)
    Optional<StepStatus> loadStepStatus(String sessionId, String stepId)
    void clearSession(String sessionId)
}

enum StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, WAITING_FOR_HUMAN }
```

---

## 16. Telemetry

```java
class LoggingHook implements AgentEventListener {
    LoggingHook(String name)
    // SLF4J logging of every AgentEvent at INFO level
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
    implements Message
record AssistantMessage(String id, Instant timestamp, String content, Map<String,Object> metadata,
                        List<ToolCall> toolCalls) implements Message
record SystemMessage(String id, Instant timestamp, String content, Map<String,Object> metadata)
    implements Message
record ToolMessage(String id, Instant timestamp, String content, Map<String,Object> metadata,
                   String toolCallId, String toolName) implements Message

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

## 19. Workflow / Orchestration

```java
enum StepStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, WAITING_FOR_HUMAN
}
```

---

## 20. Configuration Reference

### 20.1 StrandsAgentConfig

```java
record StrandsAgentConfig(
    String skillsDir,          // path to skills directory (default "skills")
    String sessionDir,         // path to session storage (default ".sessions")
    boolean llmLogEnabled,     // enable LLM call logging (default false)
    String llmLogPath,         // path for LLM log file (default "logs/llm-calls.log")
    List<String> initialSkills,// skills to activate on startup
    boolean skillSearchEnabled,// enable skill search tool (default false)
    boolean mcpIngestEnabled,  // enable MCP ingest as skills (default false)
    String mcpConfigPath,      // path to MCP server config JSON (default "config/MCP_SERVER_CONFIG.json")
    String workspace,          // workspace root for file tools
    boolean bashAllowed,       // allow bash execution tool (default false)
    boolean httpAllowPrivate,  // allow HTTP calls to private IPs (default false)
    String extraTools,         // FQCN of extra tool classes, comma-separated
    String hitlTools,          // tool names requiring human approval, comma-separated
    String hitlEmailRecipient  // email recipient for HITL notifications
)
```

### 20.2 Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `OPENAI_API_KEY` | Single-provider API key | — |
| `OPENAI_BASE_URL` | Single-provider base URL | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | Single-provider model name | `gpt-4o` |
| `LLM_TEMPERATURE` | Model temperature | `0.7` |
| `LLM_MAX_RETRIES` | Max LLM retry attempts | `3` |
| `LLM_DEFAULT_TIER` | Default model tier (simple/advanced/routing) | `simple` |
| `SIMPLE_PROVIDER` | Simple tier provider | `openai` |
| `SIMPLE_MODEL` | Simple tier model | `gpt-4o-mini` |
| `SIMPLE_API_KEY` | Simple tier API key | — |
| `SIMPLE_BASE_URL` | Simple tier base URL | — |
| `SIMPLE_OLLAMA_BASE_URL` | Simple tier Ollama URL | — |
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
| `STRANDS_AGENT_WORKSPACE` | Workspace directory for file tools | — |
| `STRANDS_SKILLS_SEARCH` | Enable skill search | `false` |
| `STRANDS_MCP_CONFIG` | MCP config file path | `config/MCP_SERVER_CONFIG.json` |
| `STRANDS_MCP_INGEST` | Enable MCP ingest | `false` |
| `STRANDS_AGENT_HITL_TOOLS` | Tools requiring human approval | — |
| `STRANDS_HITL_EMAIL_RECIPIENT` | HITL email notification recipient | — |
| `TAVILY_API_KEY` | Tavily web search API key | — |
| `VAULT_ADDR` | Hashicorp Vault address | — |
| `VAULT_TOKEN` | Hashicorp Vault token | — |
| `VAULT_MOUNT_PATH` | Vault mount path | `secret` |
| `JSTRANDS_KEY_PATH` | AES key vault file path | `api-key.enc` |

### 20.3 AgentConfig

```java
record AgentConfig(String name, String modelName, String systemPrompt, ToolRegistry toolRegistry,
                   int maxIterations, ConversationManager conversationManager,
                   SessionManager sessionManager, ChatMemoryStore chatMemoryStore,
                   ResilienceConfig resilienceConfig, List<Plugin> plugins,
                   Path skillsDir, List<String> initialSkills,
                   StructuredOutputConfig structuredOutputConfig, Path llmLogPath,
                   TieredModelConfig tieredConfig, ModelTier modelTier)
```

---

## 21. Core API DTOs

```java
// Request DTOs

class ChatRequest {
    String prompt           // user message
    String sessionId        // optional, for continuing a session
    List<String> tools      // tool names to activate
    List<String> skills     // skill names to activate
    String systemPrompt     // optional system prompt override
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

// Response DTOs

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

## 22. A2A (Agent-to-Agent)

```java
// SubAgentExecutor runs agents as sub-agents with virtual threads
class SubAgentExecutor

// SubAgentTool — an AgentTool that delegates to another agent
class SubAgentTool implements AgentTool
    // recursion guard: max 5 levels deep

// StrandsA2AProducers — produces A2A-compatible messages
class StrandsA2AProducers
```
Configured via `AGENT_URL` env var (default `http://localhost:8080`).

---

## 23. AgentContext (ThreadLocal)

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
ThreadLocal holder accessible during agent execution. Cleared on agent completion.

---

## 24. Quickstart Examples

```java
// Minimal: ChatModel + Agent + execute
ChatModel model = OpenAiChatModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("gpt-4o")
    .build();
Agent agent = new Agent(model);
AgentResult result = agent.execute("session-1", "Hello!");
System.out.println(result.finalAnswer());

// With tools
ToolRegistry tools = new ToolRegistry();
tools.register(new CalculatorTool());
tools.register(new TimeTool());
Agent agent2 = new Agent(model, tools, new ToolExecutor());
AgentResult result2 = agent2.execute("session-2", "What time is it?");
System.out.println(result2.finalAnswer());

// With plugins + session manager
SessionManager sm = new FileSessionManager(Path.of(".sessions"));
List<Plugin> plugins = List.of(new GuardrailPlugin(...));
Agent agent3 = new Agent(model, tools, new ToolExecutor(),
    new SlidingWindowConversationManager(20, 4096), sm, ResilienceConfig.DEFAULT, plugins);
agent3.agentName("my-agent");
AgentResult result3 = agent3.execute("session-3", "Help me with...");
```
