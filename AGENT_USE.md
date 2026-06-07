# Agent Usage Guide

Complete reference for the Strands Agents SDK (Java 21) Agent class.

---

## Table of Contents

- [1. Quick Start](#1-quick-start)
- [2. Creating an Agent](#2-creating-an-agent)
- [3. Tools](#3-tools)
- [4. Conversation Management](#4-conversation-management)
- [5. Session Persistence](#5-session-persistence)
- [6. Resilience](#6-resilience)
- [7. Plugins](#7-plugins)
- [8. Hooks](#8-hooks)
- [9. Structured Output](#9-structured-output)
- [10. Events](#10-events)
- [11. Streaming](#11-streaming)
- [12. Async Execution](#12-async-execution)
- [13. AgentConfig (Builder)](#13-agentconfig-builder)
- [14. Complete Examples](#14-complete-examples)

---

## 1. Quick Start

### Minimal Agent (OpenAI)

```java
var model = ModelFactory.createOpenAiFromEnv();
var agent = new Agent(model);
AgentResult result = agent.execute("What is the capital of France?");
System.out.println(result.finalAnswer());
```

Requires `OPENAI_API_KEY` and optional `OPENAI_BASE_URL` environment variables.

### Minimal Agent (Mock for Testing)

```java
var model = new MockChatModel("Mock: %s");
var agent = new Agent(model);
AgentResult result = agent.execute("Hello");
System.out.println(result.finalAnswer()); // "Mock: Hello"
```

### Interpreting Results

```java
result.stopReason()       // COMPLETED | MAX_ITERATIONS | INTERRUPTED | ERROR
result.finalAnswer()      // String response
result.metrics()          // ExecutionMetrics: durationMs, inputTokens, outputTokens, toolCallsCount
result.generatedMessages()// List<Message> — full conversation history
result.structuredOutput() // String | null — JSON if structured output was used
result.sessionId()        // String — UUID
```

---

## 2. Creating an Agent

### 2.1 All Constructors

The Agent provides 10 constructors, each adding one more capability:

```java
// 1. Minimal — ChatModel only
new Agent(ChatModel model)

// 2. Add tools
new Agent(ChatModel model, ToolRegistry toolRegistry, ToolExecutor toolExecutor)

// 3. Add conversation manager
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager)

// 4. Add session manager
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager, SessionManager)

// 5. Add resilience (retry + circuit breaker)
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager, SessionManager, ResilienceConfig)

// 6. Add hook registry
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager, SessionManager, ResilienceConfig, HookRegistry)

// 7. Add plugins (guardrails, HITL, skills)
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager, SessionManager, ResilienceConfig, List<Plugin>)

// 8. Add plugins + hooks
new Agent(ChatModel model, ToolRegistry, ToolExecutor, ConversationManager, SessionManager, ResilienceConfig, List<Plugin>, HookRegistry)

// 9. AgentConfig builder (see §13)
new Agent(AgentConfig config)

// 10. StreamingAgent — takes StreamingChatModel (see §11)
```

All constructors with missing parameters use safe defaults (null becomes an empty `ToolRegistry`, a no-op `ToolExecutor`, etc.).

### 2.2 System Prompt

```java
agent.setSystemPrompt("You are a helpful coding assistant. Be concise.");
```

### 2.3 Context Variables

Pass additional context alongside the prompt. Available in hooks via `BeforeAgentContext.contextVariables()`:

```java
var vars = Map.of("userId", "123", "language", "de");
AgentResult result = agent.execute("Translate this", vars);
```

### 2.4 Explicit Session ID

```java
AgentResult result = agent.execute("my-session-1", "Hello", Map.of());
```

Without explicit ID, a random UUID is generated.

### 2.5 Model Selection

```java
// OpenAI (from env vars)
var model = ModelFactory.createOpenAiFromEnv();

// Mock (deterministic, no dependencies)
var model = new MockChatModel("Response: %s");

// Mock streaming
var streamingModel = new MockStreamingChatModel("Streaming: %s");

// Logging wrapper (logs all LLM I/O)
var model = new LoggingChatModel(underlyingModel);
```

---

## 3. Tools

### 3.1 ToolRegistry Builder

```java
ToolRegistry registry = ToolRegistry.builder()
    .standard()                                // all 9 core file/web tools
    .with(new CalculatorTool())                // annotated @Tool instance
    .with("de.augmentia.strandsagents.core.tools.TimeTool")  // by class name
    .include("ReadTool", "WriteTool")          // whitelist
    .exclude("BashTool")                       // blacklist
    .cwd(Path.of("/tmp"))                      // working dir for file tools
    .build();
```

**Core tools (`.standard()`):**

| Tool | Class | Description |
|------|-------|-------------|
| `BashTool` | `core.tools.BashTool` | Execute shell commands |
| `ReadTool` | `core.tools.ReadTool` | Read file contents |
| `WriteTool` | `core.tools.WriteTool` | Write content to files |
| `EditTool` | `core.tools.EditTool` | Perform file edits |
| `FindTool` | `core.tools.FindTool` | Find files by glob pattern |
| `GrepTool` | `core.tools.GrepTool` | Search file contents by regex |
| `LsTool` | `core.tools.LsTool` | List directory contents |
| `DockerRunTool` | `core.tools.DockerRunTool` | Execute command in Docker container |
| `WebFetchTool` | `core.tools.WebFetchTool` | Fetch a URL |
| `WebSearchTool` | `core.tools.WebSearchTool` | Perform a web search |

**Additional tools (not in standard):**

| Tool | Class | Description |
|------|-------|-------------|
| `CalculatorTool` | `core.tools.CalculatorTool` | `add(a,b)`, `multiply(a,b)`, `stringLength(s)` |
| `TimeTool` | `core.tools.TimeTool` | `getCurrentTime()`, `getCurrentDate()` |
| `HttpTool` | `core.tools.HttpTool` | `get(url)`, `post(url, body)` |
| `HumanInTheLoopTool` | `core.tools.HumanInTheLoopTool` | Request human input/approval |

### 3.2 Runtime Tool Manipulation

Tools can be added, removed, or swapped at any time — changes take effect on the next LLM call:

```java
agent.addTool(new CalculatorTool());            // add @Tool annotated instance
agent.addTool(new MyCustomAgentTool());          // add AgentTool<?>
agent.removeTool("BashTool");
agent.setToolRegistry(anEntirelyNewRegistry());  // swap everything at once
```

### 3.3 Tool Registration via Plugins

The `Plugin.getTools()` method returns `List<ToolRegistry.ToolMethod>`. These are registered into the agent's `ToolRegistry` during plugin initialization — see §7.

### 3.4 Tool Execution

When the LLM requests a tool execution, the agent:
1. Fires the `beforeToolCall` hook (can cancel the tool call)
2. Executes the tool via `ToolExecutor`
3. Fires the `afterToolCall` hook (can modify the result)
4. Feeds the result back into the conversation loop

---

## 4. Conversation Management

Controls how conversation history is pruned between iterations.

### 4.1 Sliding Window

Keeps only the last N messages:

```java
ConversationManager cm = new SlidingWindowConversationManager(10);
```

### 4.2 Summarizing

When the estimated token count exceeds `maxTokens`, summarizes the oldest half into a system message, keeping the rest intact:

```java
ChatModel summarizer = ModelFactory.createOpenAiFromEnv();
ConversationManager cm = new SummarizingConversationManager(summarizer, 2048);
```

### 4.3 No Conversation Manager

Pass `null` — conversation history grows unbounded. The LLM sees the entire message history on every call.

---

## 5. Session Persistence

Sessions allow an agent run to span across multiple `execute()` calls. The agent loads previous conversation state and continues from where it left off.

### 5.1 File Session Manager

```java
SessionManager sm = new FileSessionManager(Path.of(".sessions"));
```

Sessions are stored as `{sessionId}.json` files. Uses file locking for concurrent safety.

### 5.2 JDBC Session Manager

```java
DataSource ds = ...
SessionManager sm = new JdbcSessionManager(ds);
```

Stores sessions in a relational database via `MERGE` statements (auto-creates schema).

### 5.3 Using Sessions

```java
var agent = new Agent(model, tools, executor, null, sm);

// First call — creates session
AgentResult r1 = agent.execute("session-1", "Hello", Map.of());

// Second call — resumes session-1
AgentResult r2 = agent.execute("session-1", "What did I just say?", Map.of());
```

The session is **automatically persisted** after each `execute()` call, including the full message history and agent state (COMPLETED / FAILED).

### SessionManager API

```java
Session s = sm.createSession("agent-name", metadata);
Optional<Session> loaded = sm.loadSession("session-1");
sm.saveSession(updatedSession);
sm.deleteSession("session-1");
List<Session> all = sm.listSessions("agent-name");
List<Session> found = sm.searchByMetadata("key", "value");
```

---

## 6. Resilience

### 6.1 Retry

Exponential backoff on LLM failures:

```java
RetryConfig retry = new RetryConfig(
    3,            // maxAttempts
    1000,         // backoffDelayMs (initial delay)
    2.0           // backoffMultiplier (exponential)
);
```

### 6.2 Circuit Breaker

Prevents cascading failures by tripping open after a threshold:

```java
CircuitBreakerConfig cb = new CircuitBreakerConfig(
    0.5f,     // failureRateThreshold (50%)
    10,       // slidingWindowSeconds (window for counting failures)
    30        // halfOpenDelaySeconds (before trying again)
);
```

### 6.3 Combined

```java
ResilienceConfig resilience = new ResilienceConfig(retry, cb);
// or use predefined:
ResilienceConfig resilience = ResilienceConfig.DEFAULT; // Retry(3, 1000, 2.0) + CB(0.5, 10, 30)
```

Pass `ResilienceConfig.NONE` or `null` to disable all resilience.

```java
var agent = new Agent(model, tools, executor, null, null, resilience);
```

---

## 7. Plugins

### 7.1 Guardrail Plugin

Validates input (before LLM call) and output (after LLM call) against configurable guardrails. Three block actions:

```java
GuardrailPlugin guardrails = new GuardrailPlugin(
    List.of(inputGuardrail1, inputGuardrail2),   // input validators
    List.of(outputGuardrail1),                    // output validators
    BlockAction.FALLBACK,                         // THROW | FALLBACK | ESCALATE
    "This content cannot be processed."           // fallback message
);
```

Guardrail is a functional interface:
```java
Guardrail keywordFilter = (messages, context) -> {
    if (messages.stream().anyMatch(m -> m.text().contains("secret"))) {
        return GuardrailResult.failed("Contains forbidden keyword");
    }
    return GuardrailResult.ok();
};
```

### 7.2 HITL (Human-in-the-Loop) Plugin

Controls whether tool execution requires human approval:

```java
HITLPlugin hitl = new HITLPlugin(
    consoleProvider(),               // HITLProvider implementation
    HITLAuthority.CONFIRM            // AUTO | CONFIRM | REVIEW | DENY
);
```

**Authority levels:**
| Value | Behavior |
|-------|----------|
| `AUTO` | Proceed without human intervention |
| `CONFIRM` | Require human confirmation before each tool execution |
| `REVIEW` | Execute but log for human review |
| `DENY` | Block all tool execution |

**Built-in provider:** `HITLHook.consoleProvider()` reads stdin for yes/no confirmation.

### 7.3 Skills Plugin

Injects skill descriptions as XML into the system prompt so the LLM can use them as context:

```java
List<Skill> skills = List.of(
    new Skill("code-review", "Review Java code", "Check for: null safety...", ...)
);
AgentSkillsPlugin skillsPlugin = new AgentSkillsPlugin(skills);
skillsPlugin.setSkillSearchEnabled(true);
```

The plugin injects `<activated_skills>` and `<available_skills>` XML into the system prompt before every LLM call. When `skillSearchEnabled`, the `SkillSearchTool` is also registered so the LLM can query and activate skills at runtime.

### 7.4 Combining Plugins

```java
var agent = new Agent(model, tools, executor, null, null, null,
    List.of(guardrails, hitl, skillsPlugin));
```

---

## 8. Hooks

Hooks intercept the agent execution at 6 points and can modify, cancel, or retry the current step.

### 8.1 Hook Interface

```java
public interface AgentHook {
    String name();

    default HookResult beforeAgent(BeforeAgentContext ctx);
    default HookResult afterAgent(AfterAgentContext ctx, String response);

    default HookResult beforeModelCall(BeforeModelCallContext ctx);
    default HookResult afterModelCall(AfterModelCallContext ctx, String llmResponse);

    default HookResult beforeToolCall(BeforeToolCallContext ctx);
    default HookResult afterToolCall(AfterToolCallContext ctx, String toolResult);
}
```

### 8.2 HookResult Types

```java
sealed interface HookResult permits Continue, Cancel, Modify, Retry {
    record Continue() {}                                   // proceed unchanged
    record Cancel(String reason) {}                        // abort execution
    record Modify<T>(T value) {}                           // replace the value
    record Retry(String reason) {}                         // re-run the LLM call
}
```

**Where each result type is supported:**

| Method | Continue | Cancel | Modify | Retry |
|--------|----------|--------|--------|-------|
| `beforeAgent` | ✓ | ✓ | `Modify<String>` (prompt) | — |
| `afterAgent` | ✓ | — | `Modify<String>` (response) | — |
| `beforeModelCall` | ✓ | ✓ | `Modify<List<ToolSpecification>>` (tools) | — |
| `afterModelCall` | ✓ | ✓ | `Modify<String>` (response) | ✓ |
| `beforeToolCall` | ✓ | ✓ | — | — |
| `afterToolCall` | ✓ | — | `Modify<String>` (result) | — |

### 8.3 Hook Contexts

```java
// §8.3.1 — BeforeAgentContext
new BeforeAgentContext(
    String sessionId,
    String prompt,                  // use Modify<> to change
    Map<String, Object> contextVariables
)

// §8.3.2 — AfterAgentContext
new AfterAgentContext(
    String sessionId,
    AgentResult result
)

// §8.3.3 — BeforeModelCallContext
new BeforeModelCallContext(
    String sessionId,
    StringBuilder systemPrompt,      // mutable — modify in-place
    List<Message> messages,          // current conversation
    List<ToolSpecification> tools     // replace via Modify<>
)

// §8.3.4 — AfterModelCallContext
new AfterModelCallContext(
    String sessionId,
    String llmResponse,
    int inputTokens,
    int outputTokens
)

// §8.3.5 — BeforeToolCallContext
new BeforeToolCallContext(
    String sessionId,
    String toolName,
    Map<String, Object> arguments
)

// §8.3.6 — AfterToolCallContext
new AfterToolCallContext(
    String sessionId,
    String toolName,
    String result,
    boolean isError
)
```

### 8.4 Hook Examples

**Logging hook — all 6 points:**

```java
AgentHook loggingHook = new AgentHook() {
    @Override public String name() { return "logging"; }

    @Override
    public HookResult beforeAgent(BeforeAgentContext ctx) {
        System.out.println("[hook] executing: " + ctx.prompt());
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterAgent(AfterAgentContext ctx, String response) {
        long ms = ctx.result().metrics().durationMs();
        System.out.println("[hook] completed in " + ms + "ms");
        return new HookResult.Continue();
    }

    @Override
    public HookResult beforeModelCall(BeforeModelCallContext ctx) {
        System.out.println("[hook] LLM call with "
            + ctx.messages().size() + " messages, "
            + ctx.tools().size() + " tools");
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterModelCall(AfterModelCallContext ctx, String response) {
        System.out.println("[hook] LLM responded "
            + response.length() + " chars");
        return new HookResult.Modify<>(response);
    }

    @Override
    public HookResult beforeToolCall(BeforeToolCallContext ctx) {
        System.out.println("[hook] tool: " + ctx.toolName()
            + " args: " + ctx.arguments());
        return new HookResult.Continue();
    }

    @Override
    public HookResult afterToolCall(AfterToolCallContext ctx, String result) {
        System.out.println("[hook] tool " + ctx.toolName()
            + " → " + (ctx.isError() ? "ERROR" : result.length() + " chars"));
        return new HookResult.Modify<>(result);
    }
};
```

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

**Replace tools for a single call:**

```java
AgentHook restrictToolsHook = new AgentHook() {
    @Override public String name() { return "restrict-tools"; }

    @Override
    public HookResult beforeModelCall(BeforeModelCallContext ctx) {
        var restricted = ctx.tools().stream()
            .filter(t -> !t.name().equals("BashTool"))
            .toList();
        return new HookResult.Modify<>(restricted);
    }
};
```

**Modify the input prompt:**

```java
AgentHook prefixHook = new AgentHook() {
    @Override public String name() { return "prefix"; }

    @Override
    public HookResult beforeAgent(BeforeAgentContext ctx) {
        return new HookResult.Modify<>(
            "You are an expert. " + ctx.prompt());
    }
};
```

**Retry LLM call on specific response:**

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

### 8.5 HookRegistry

```java
HookRegistry hooks = new HookRegistry();
hooks.register(loggingHook);
hooks.register(securityHook);
hooks.setFailurePolicy(HookFailurePolicy.CHAIN_ABORT);
// ISOLATE (default): failing hooks are silently skipped
// CHAIN_ABORT: failing hooks return Cancel

// Pass to agent:
var agent = new Agent(model, tools, executor, null, null, null, null, hooks);
```

---

## 9. Structured Output

Force the LLM to respond in a structured JSON format matching a Java class or schema.

### 9.1 Static Mode (from Java class)

```java
agent.setStructuredOutputModel(AgentResult.class);
// or via config:
agent.setStructuredOutputConfig(
    StructuredOutputConfig.staticModel(AgentResult.class));
```

Generates a JSON Schema from the record's fields (supports records, Lists, arrays, primitives). The LLM is instructed to respond with valid JSON matching that schema.

### 9.2 Dynamic Mode (from raw JSON Schema)

```java
agent.setStructuredOutputConfig(
    StructuredOutputConfig.dynamicSchema("""
    {
        "type": "object",
        "properties": {
            "answer": {"type": "string"},
            "confidence": {"type": "number"}
        }
    }
    """));
```

### 9.3 Force Prompt

When the LLM fails to produce valid JSON, the agent retries with a force prompt:

```java
StructuredOutputConfig config = StructuredOutputConfig.staticModel(
    MyRecord.class,
    "You MUST output valid JSON matching the provided schema. No explanation."
);
```

### 9.4 Accessing Structured Output

```java
AgentResult result = agent.execute("Extract: John is 30 years old");
String json = result.structuredOutput(); // {"name":"John","age":30}
```

---

## 10. Events

The agent publishes lifecycle events. Subscribe via listener or reactive stream.

### 10.1 AgentEventListener

```java
agent.setEventListener(new AgentEventListener() {
    @Override
    public void onEvent(AgentEvent event) {
        System.out.println(event.getClass().getSimpleName() + ": " + event);
    }
});
```

### 10.2 Reactive Streams

```java
Flow.Publisher<AgentEvent> stream = agent.eventStream();
stream.subscribe(new Flow.Subscriber<>() {
    @Override public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
    @Override public void onNext(AgentEvent event) { /* handle */ }
    @Override public void onError(Throwable t) {}
    @Override public void onComplete() {}
});

// Or subscribe temporarily for a single execution:
CompletableFuture<AgentResult> future = agent.executeEvents(prompt, subscriber);
```

### 10.3 Event Types

| `AgentStartedEvent(sessionId, timestamp, prompt)` | Execution begins |
| `AgentFinishedEvent(sessionId, timestamp, answer)` | Execution ends |
| `ModelRequestedEvent(sessionId, timestamp, messages)` | LLM call starts |
| `AfterInvocationEvent(sessionId, timestamp, response, messages)` | LLM call completes |
| `ToolExecutionStartedEvent(sessionId, timestamp, toolCall)` | Tool execution starts |
| `ToolExecutionFinishedEvent(sessionId, timestamp, result)` | Tool execution completes |
| `BeforeInvocationEvent(sessionId, timestamp, systemPrompt, messages)` | Before each iteration |
| `TokenEvent(sessionId, timestamp, token)` | Streaming token received |
| `AgentStateChangedEvent(sessionId, timestamp, from, to)` | Phase transitions |

---

## 11. Streaming

Use `StreamingAgent` for token-by-token LLM responses.

### 11.1 Constructors

```java
StreamingAgent agent = new StreamingAgent(streamingModel);
StreamingAgent agent = new StreamingAgent(streamingModel, tools, executor);
StreamingAgent agent = new StreamingAgent(streamingModel, tools, executor, convMgr);
StreamingAgent agent = new StreamingAgent(streamingModel, tools, executor, convMgr, sessionMgr);
StreamingAgent agent = new StreamingAgent(streamingModel, tools, executor, convMgr, sessionMgr, resilience);
StreamingAgent agent = new StreamingAgent(streamingModel, tools, executor, convMgr, sessionMgr, resilience, plugins);
```

### 11.2 Stream Execution

```java
var model = ModelFactory.createOpenAiFromEnv(); // or MockStreamingChatModel
StreamingAgent agent = new StreamingAgent(model);

// Synchronous with token handler:
agent.executeStreaming("Tell me a story", token -> {
    System.out.print(token);  // prints tokens as they arrive
});

// Async:
agent.executeStreamingAsync("Tell me a story", token -> {
    System.out.print(token);
});
```

Internally, `StreamingAgent` wraps the `StreamingChatModel` in a `StreamingModelBridge` that accumulates tokens and fires `TokenEvent`. The `executeStreaming()` method sets the token handler on the bridge, then calls the normal `execute()` loop.

---

## 12. Async Execution

```java
CompletableFuture<AgentResult> future = agent.executeAsync("Hello");
future.thenAccept(result -> System.out.println(result.finalAnswer()));

// With context variables:
CompletableFuture<AgentResult> future = agent.executeAsync("Hello", vars);
```

Uses `VIRTUAL_EXECUTOR` (`Executors.newVirtualThreadPerTaskExecutor()`) — each async execution runs on a virtual thread.

---

## 13. AgentConfig (Builder)

A builder-driven alternative to the long constructor chain:

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
    .build()                   // returns AgentConfig
    .createAgent();            // creates Agent using ModelFactory.createOpenAiFromEnv()

// Or pass an explicit model:
Agent agent = config.createAgent(myCustomModel);
```

When `logLlmCalls` is set, the agent is wrapped with `LoggingChatModel` that JSON-logs every LLM request/response.

---

## 14. Complete Examples

### 14.1 Minimal File Editor Agent

```java
var model = ModelFactory.createOpenAiFromEnv();

var tools = ToolRegistry.builder()
    .standard()
    .exclude("BashTool", "WebFetchTool", "WebSearchTool")
    .build();

var agent = new Agent(model, tools, new ToolExecutor());
agent.setSystemPrompt("You are a file editing assistant. Read, write, and edit files.");

AgentResult result = agent.execute(
    "Read src/main.java, find the bug, and fix it.");
System.out.println(result.finalAnswer());
```

### 14.2 Research Agent with Session and Resilience

```java
var model = ModelFactory.createOpenAiFromEnv();

var tools = ToolRegistry.builder()
    .standard()
    .with(new CalculatorTool())
    .build();

var conv = new SlidingWindowConversationManager(20);
var session = new FileSessionManager(Path.of(".research-sessions"));
var resilience = ResilienceConfig.DEFAULT;

var agent = new Agent(model, tools, new ToolExecutor(),
    conv, session, resilience);
agent.setSystemPrompt("You are a research assistant. Use web search and fetch to answer.");

// Multi-turn research session
agent.execute("research-1", "Find the latest Java version");
agent.execute("research-1", "What are its key features?");
agent.execute("research-1", "Summarize everything so far");
```

### 14.3 Guarded Agent with HITL, Hooks, and Streaming

```java
// Guardrails
Guardrail keywordGuard = (msgs, ctx) -> msgs.stream()
    .anyMatch(m -> m.text().contains("password"))
    ? GuardrailResult.failed("password detected")
    : GuardrailResult.ok();

var guardrails = new GuardrailPlugin(
    List.of(keywordGuard),
    List.of(),
    BlockAction.FALLBACK,
    "Sensitive content blocked.");

// HITL
var hitl = new HITLPlugin(HITLHook.consoleProvider(), HITLAuthority.CONFIRM);

// Logging hook
AgentHook logger = new AgentHook() {
    @Override public String name() { return "timing"; }

    @Override
    public HookResult afterModelCall(AfterModelCallContext ctx, String response) {
        System.out.println("LLM call: " + ctx.inputTokens() + " in, "
            + ctx.outputTokens() + " out");
        return new HookResult.Continue();
    }

    @Override
    public HookResult beforeToolCall(BeforeToolCallContext ctx) {
        System.out.println("Executing: " + ctx.toolName());
        return new HookResult.Continue();
    }
};

var hooks = new HookRegistry();
hooks.register(logger);

var model = ModelFactory.createOpenAiFromEnv();
var agent = new Agent(model, tools, new ToolExecutor(),
    null, null, null,
    List.of(guardrails, hitl), hooks);
agent.setSystemPrompt("You are a guarded assistant.");

AgentResult result = agent.execute("What is my password?");
System.out.println(result.finalAnswer());
// → "Sensitive content blocked."
```

### 14.4 Structured Extraction Agent

```java
record Person(String name, int age, List<String> hobbies) {}

var model = ModelFactory.createOpenAiFromEnv();
var agent = new Agent(model);
agent.setSystemPrompt("Extract structured information from text.");
agent.setStructuredOutputConfig(
    StructuredOutputConfig.staticModel(Person.class,
        "Output valid JSON only."));

AgentResult result = agent.execute(
    "John is 30 years old and enjoys hiking, reading, and coding.");
String json = result.structuredOutput();
// {"name":"John","age":30,"hobbies":["hiking","reading","coding"]}
```

### 14.5 Multi-Agent with Skills

```java
// Skill definitions
var codeReviewSkill = new Skill(
    "code-review", "Review Java code for best practices",
    "Check for: null safety, exception handling, thread safety", ...);
var docGenSkill = new Skill(
    "doc-gen", "Generate JavaDoc documentation",
    "Add JavaDoc to all public methods and classes", ...);

// Skills plugin
var skillsPlugin = new AgentSkillsPlugin(
    List.of(codeReviewSkill, docGenSkill),
    List.of("code-review"));  // pre-activate code-review

// Tools for code manipulation
var tools = ToolRegistry.builder()
    .standard()
    .exclude("WebSearchTool", "WebFetchTool")
    .build();

var model = ModelFactory.createOpenAiFromEnv();
var agent = new Agent(model, tools, new ToolExecutor(),
    null, null, null, List.of(skillsPlugin));
agent.setSystemPrompt("You are a code quality assistant.");

agent.execute("Review the file src/com/example/Service.java");
```

### 14.6 Custom Hook - Dynamic Tool Injection

```java
AgentHook toolInjector = new AgentHook() {
    @Override public String name() { return "tool-injector"; }

    @Override
    public HookResult beforeModelCall(BeforeModelCallContext ctx) {
        // Add a tool only available for this specific call
        var extended = new ArrayList<>(ctx.tools());
        if (ctx.messages().stream().anyMatch(m ->
                m.text().contains("calculate"))) {
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

### 14.7 Full Configuration via AgentConfig Builder

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
        new GuardrailPlugin(List.of(), List.of(),
            BlockAction.FALLBACK, "Blocked"),
        new HITLPlugin(HITLHook.consoleProvider(),
            HITLAuthority.AUTO)))
    .structuredOutputModel(ExtractionResult.class)
    .logLlmCalls(Path.of("logs/llm.json"))
    .build()
    .createAgent();
```

---

## Appendix: Execution Flow Diagram

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
