# Architecture Roadmap — Strands Agents (Java)

## Current State

The codebase is a **layered monolith** within two Maven modules:

```
strands-agents/          (core library, no framework dependency)
  core/agent/            → Agent.executeLoop() monolithic orchestrator
  core/config/           → Environment-based config (ConfigReader)
  core/conversation/     → Conversation history management
  core/hook/             → AgentHook interceptor system (before/after model/tool)
  core/plugin/           → Plugin interface (minimal: name, initAgent, getTools)
  core/plugin/guardrail/ → GuardrailPlugin (hardcoded in executeLoop)
  core/plugin/hitl/      → HITLPlugin + CheckpointHook
  core/logging/          → File-based LLM call logging
  core/mcp/              → MCP client connector
  core/model/            → Domain model (events, messages, sessions, API DTOs)
  core/prompt/           → Prompt management (YAML-based)
  core/resilience/       → Retry + CircuitBreaker
  core/tools/            → ToolRegistry + 20+ tool implementations
  core/workflow/         → Workflow definition + coordinator
  sessions/              → SessionManager (FileSessionManager, JdbcSessionManager)
  skills/                → Skill parser, registry, discovery plugins
  telemetry/             → Tracing, metrics, logging hooks
  vault/                 → Secret providers (Vault, File, Composite)

strands-agents-quarkus/  (Quarkus adapter)
  resources/             → REST endpoints (Chat, Admin, Sessions, Tools, Keys, UI)
  service/               → AgentService (orchestrator), SecretService
  a2a/                   → Agent-to-Agent protocol
  agui/                  → AGUI protocol
```

### Known Problems

| Problem | Description |
|---------|-------------|
| **P1** | `Agent.executeLoop()` is a 150-line monolithic method with hardcoded guardrail/HITL/skill-injection logic |
| **P2** | Plugin system is minimal — plugins can't hook into the agent lifecycle phases |
| **P3** | GuardrailPlugin is referenced directly in executeLoop, not through an abstraction |
| **P4** | Hook system and Plugin system overlap (both do before/after hooks) but don't integrate |
| **P5** | AgentHook has no ordering — hook execution order is undefined |
| **P6** | Secret providers are hard-referenced in SecretService (no SPI) |
| **P7** | Package structure is technical layers (core.agent, core.config, core.plugin) not domain features |
| **P8** | No feature toggles — all code is either compiled in or not |
| **P9** | Event system is synchronous direct calls (CopyOnWriteArrayList) — no central bus |

---

## Vision: Feature-Driven, Pluggable Architecture

```
                    ┌──────────────────────────────────────┐
                    │         Agent.executeLoop()           │
                    │  (plugin-agnostic orchestrator)       │
                    └────┬─────┬──────┬──────┬─────────────┘
                         │     │      │      │
              ┌──────────┘     │      │      └──────────┐
              ▼                ▼      ▼                  ▼
    ┌──────────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  Plugin Chain     │ │ Pipeline │ │ EventBus │ │ SPI       │
    │  (ordered)        │ │ (ordered)│ │ (async)  │ │ (modules) │
    └──────────────────┘ └──────────┘ └──────────┘ └──────────┘
              │                │             │             │
    ┌─────────┼──────────┐     │             │             │
    ▼         ▼          ▼     ▼             ▼             ▼
┌────────┐ ┌────────┐ ┌────┐ ┌────────┐ ┌────────┐ ┌──────────────┐
│Guardrail│ │ HITL   │ │Skll│ │Anonym. │ │Tracing │ │strands-secret│
│Plugin  │ │ Plugin │ │Plgn│ │Pipeline│ │Metrics │ │-provider-aws │
└────────┘ └────────┘ └────┘ └────────┘ └────────┘ └──────────────┘
```

### Guiding Principles

1. **Closed Loop, Open Plugins** — `executeLoop()` never changes for new features
2. **Ordered Execution** — All hooks/interceptors have a deterministic order
3. **Single Responsibility** — One plugin = one concern (guardrails, HITL, skills, ...)
4. **Package by Feature** — Code lives with its feature, not its layer
5. **Progressive Enhancement** — No big-bang rewrite; incremental, backward-compatible steps

---

## Initiative 1: Unified Plugin Architecture

### Goal
Replace the three separate mechanisms (GuardrailPlugin hardcoded, HITL via CheckpointHook, AgentSkillsPlugin via setPluginHook) with a single, powerful Plugin interface that hooks into all agent lifecycle phases.

### Current Code

```java
// Plugin.java (current)
public interface Plugin {
    String name();
    default void initAgent(Agent agent) {}
    default List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
}
```

```java
// In Agent.executeLoop() — hardcoded, not abstracted
if (guardrailPlugin != null) {
    for (var g : guardrailPlugin.inputGuardrails()) {
        var result = g.validate(messages, guardrailPlugin.name());
        // ...
    }
}
```

### Target

```java
// Plugin.java (target)
public interface Plugin {
    String name();
    default int order() { return 0; }

    // Lifecycle
    default void onInit(Agent agent) {}
    default void onDestroy() {}

    // Extension Points — implement only what you need
    default HookResult onBeforeModelCall(BeforeModelCallContext ctx) { return HookResult.continue_(); }
    default HookResult onAfterModelCall(AfterModelCallContext ctx) { return HookResult.continue_(); }
    default HookResult onBeforeToolCall(BeforeToolCallContext ctx) { return HookResult.continue_(); }
    default HookResult onAfterToolCall(AfterToolCallContext ctx) { return HookResult.continue_(); }

    // Guardrails
    default List<Guardrail> getInputGuardrails() { return List.of(); }
    default List<Guardrail> getOutputGuardrails() { return List.of(); }

    // Tools
    default List<ToolRegistry.ToolMethod> getTools() { return List.of(); }
}
```

```java
// In Agent.executeLoop() — plugin-agnostic
for (var plugin : orderedPlugins) {
    for (var g : plugin.getInputGuardrails()) {
        var result = g.validate(messages, plugin.name());
    }
    var hookResult = plugin.onBeforeModelCall(ctx);
    // handle continue / cancel / modify
}
```

### Changes

| File | Change |
|------|--------|
| `core/plugin/Plugin.java` | Add `order()`, `onBeforeModelCall()`, `onAfterModelCall()`, `onBeforeToolCall()`, `onAfterToolCall()`, `getInputGuardrails()`, `getOutputGuardrails()` |
| `core/plugin/GuardrailPlugin.java` | Migrate to new Plugin interface; remove direct guardrail references from Agent |
| `core/plugin/hitl/HITLPlugin.java` | Migrate CheckpointHook into `onBeforeToolCall()` |
| `skills/AgentSkillsPlugin.java` | Migrate skill XML injection into `onBeforeModelCall()` |
| `core/agent/Agent.java` | Extract hardcoded guardrail/HITL/skill logic; iterate plugins instead |
| `core/agent/AgentFactory.java` | Sort plugins by `order()` |
| `core/plugin/PluginRegistry.java` | Simplify or remove (direct integration in Plugin) |
| `core/hook/GuardrailHook.java`, `HITLHook.java`, `CheckpointHook.java` | Remove (logic moves into plugins) |

### Why This Order
- Guardrails, HITL, and skills are the THREE coupling points in executeLoop
- All three can be extracted without changing the loop's control flow
- No behavioral change — same guards, same hooks, just behind a uniform interface

---

## Initiative 2: Ordered Hook Pipeline

### Goal
Make the `AgentHook` execution order deterministic by adding an `order()` method. Merge duplicate hook registries.

### Current Code

```java
public interface AgentHook {
    // All methods are optional (default return Continue)
    default HookResult beforeAgent(BeforeAgentContext ctx) { ... }
    default HookResult afterAgent(AfterAgentContext ctx, String response) { ... }
    default HookResult beforeModelCall(BeforeModelCallContext ctx) { ... }
    default HookResult afterModelCall(AfterModelCallContext ctx, String response) { ... }
    default HookResult beforeToolCall(BeforeToolCallContext ctx) { ... }
    default HookResult afterToolCall(AfterToolCallContext ctx, String result) { ... }
}
```

There is **no `order()`**. Hook execution order depends on insertion order into HookRegistry, which is undocumented and accidental.

### Target

```java
public interface AgentHook {
    default int order() { return 0; }
    // ... same methods
}
```

And `HookRegistry` sorts by order before execution.

### Changes

| File | Change |
|------|--------|
| `core/hook/AgentHook.java` | Add `default int order()` |
| `core/hook/HookRegistry.java` | Sort hooks by `order()` in constructor |

### Why This Order
Minimal change, low risk, immediately useful for anyone writing hooks.

---

## Initiative 3: Unified Package Structure (Package-by-Feature)

### Goal
Migrate from layered packages (`core.agent.*`, `core.plugin.*`, `core.tools.*`) to feature-oriented packages (`features.hitl.*`, `features.skills.*`, `features.resilience.*`).

### Current

```
core/
  agent/         → Agent, AgentFactory, RoutingAgent, StreamingAgent, ...
  plugin/        → Plugin, PluginRegistry
  plugin/guardrail/ → GuardrailPlugin
  plugin/hitl/   → HITLPlugin, CheckpointHook, CheckpointService, ...
  hook/          → HookRegistry, AgentHook, HookResult, HookContexts
  conversation/  → ConversationManager, SlidingWindow, Summarizing
  resilience/    → Retry, CircuitBreaker, ResilienceConfig
  tools/         → ToolRegistry, BashTool, ReadTool, WebFetchTool, ...
```

### Target

```
core/
  Agent.java              → Orchestrator (executeLoop, session management)
  AgentFactory.java       → Factory methods
  ToolRegistry.java       → Tool registration + discovery
  AgentEventListener.java → Event listener interface
features/
  guardrails/
    GuardrailPlugin.java
    Guardrail.java / GuardrailResult.java
    BlockAction.java
  hitl/
    HITLPlugin.java
    CheckpointService.java, CheckpointHook.java
    CheckpointChannel.java, ConsoleChannel.java, EmailChannel.java, SSEChannel.java
  skills/
    AgentSkillsPlugin.java
    Skill.java, SkillParser.java
    SkillSearchTool.java
  mcp/
    McpConnector.java
    McpServerConfigLoader.java
    McpToolMethod.java
  resilience/
    Retry.java / RetryConfig.java
    CircuitBreaker.java / CircuitBreakerConfig.java
    TokenRecovery.java
  telemetry/
    LoggingHook.java
    MetricsHook.java
    TracingHook.java
  secrets/
    SecretProvider.java (Interface)
    AwsSsmProvider.java, GcpSecretManagerProvider.java
    CloudSecretProviderFactory.java
config/
  StrandsAgentConfig.java
  ConfigReader.java
  ModelFactory.java, ModelProviderType.java, ModelTier.java
model/
  event/   → AgentEvent + subtypes
  message/ → Message + subtypes
  session/ → Session
  api/     → ChatRequest, ChatResponse, AgentInitRequest, ToolInfo, ...
prompt/
  PromptManager, PromptRegistry, YamlPromptManager
```

### Changes
- Move files to new packages (no code changes, just relocation)
- Update imports across the codebase
- Update package declarations

### Why This Order
Pure refactoring — no behavioral change, low risk. Should be done early because subsequent changes are easier with the new structure.

---

## Initiative 4: Feature Toggles

### Goal
Make features declaratively enableable/disableable via YAML configuration, allowing safe rollout of new capabilities.

### New File: `features.yaml`

```yaml
features:
  hitl:
    enabled: true
  mcp-ingest:
    enabled: true
  skills-search:
    enabled: true
  advanced-summarizer:
    enabled: false
    strategy: "TREE_OF_THOUGHT"
```

### Changes

| File | Change |
|------|--------|
| `core/config/ConfigReader.java` | Load `features.yaml`, provide `isEnabled("featureName")` |
| `core/agent/AgentFactory.java` | Check feature toggles before registering optional tools/plugins |
| `quarkus/service/AgentService.java` | Check feature toggles in ensureInitialized() |

---

## Initiative 5: SPI Modules (Service Provider Interface)

### Goal
Allow third-party implementations of SecretProvider, SessionManager, and ToolRegistry without modifying core code.

### Mechanism

```java
// In CloudSecretProviderFactory
var providers = ServiceLoader.load(SecretProvider.class).stream()
    .map(ServiceLoader.Provider::get)
    .toList();
```

Each module declares:

```
META-INF/services/de.augmentia.strandsagents.core.secret.SecretProvider
→ com.example.MySecretProvider
```

### New Maven Modules

```
strands-secret-provider-aws/     → AwsSsmProvider
strands-secret-provider-gcp/    → GcpSecretManagerProvider
strands-session-store-jdbc/     → JdbcSessionManager
strands-tool-search/            → WebSearchTool, WebFetchTool
```

### Parent POM

```xml
<modules>
    <module>strands-agents</module>
    <module>strands-agents-quarkus</module>
    <module>strands-secret-provider-aws</module>
    <module>strands-secret-provider-gcp</module>
</modules>
```

---

## Initiative 6: Async Event Bus (Future)

### Current
Events are fired synchronously via `CopyOnWriteArrayList<AgentEventListener>`.

### Target (when needed)
Replace with Quarkus Vert.x EventBus:

```java
@Inject EventBus eventBus;

void fire(AgentEvent event) {
    eventBus.publish("agent-events", event);
}
```

Consumers:

```java
@ApplicationScoped
public class MetricsEventConsumer {
    @ConsumeEvent("agent-events")
    void onEvent(AgentEvent event) {
        // async, non-blocking
    }
}
```

### When to Implement
- When throughput exceeds ~100 req/s
- When tracing/metrics consumers block the agent loop measurably
- When background tool execution is introduced

---

## Summary

| # | Initiative | Status | Risk | Effort | Dependencies |
|---|-----------|--------|------|--------|-------------|
| 1 | Unified Plugin Architecture | Planned | Medium | 2-3 days | None |
| 2 | Ordered Hook Pipeline | Planned | Low | 0.5 day | None |
| 3 | Package-by-Feature | Planned | Low | 1-2 days | None |
| 4 | Feature Toggles | Planned | Low | 0.5 day | None |
| 5 | SPI Modules | Planned | Medium | 2-3 days | Initiative 1 |
| 6 | Async Event Bus | Future | High | 1-2 days | Performance data needed |
