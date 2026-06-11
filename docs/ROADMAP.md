# Implementation Roadmap

Tracks all implementation steps with current status. Updated after each completed step.

---

## Initiative 1: Unified Plugin Architecture

| Step | Description | Status |
|------|-------------|--------|
| 1.1 | Enrich `Plugin.java` interface: add `order()`, `onBeforeModelCall()`, `onAfterModelCall()`, `onBeforeToolCall()`, `onAfterToolCall()`, `getInputGuardrails()`, `getOutputGuardrails()` | ✅ Done |
| 1.2 | Migrate `GuardrailPlugin` to new Plugin interface | ✅ Done |
| 1.3 | Migrate `HITLPlugin` (CheckpointHook → `onBeforeToolCall()`) | ✅ Done |
| 1.4 | Migrate `AgentSkillsPlugin` (skill XML injection → `onBeforeModelCall()`) | ✅ Done |
| 1.5 | Make `Agent.executeLoop()` plugin-agnostic — iterate `plugins` instead of hardcoded references | ✅ Done |
| 1.6 | Simplify or remove `PluginRegistry` | ✅ Done |
| 1.7 | Remove deprecated classes: `GuardrailHook`, `HITLHook`, `CheckpointHook` | ✅ Done |
| 1.8 | Verify all existing tests pass + no behavioral regression | ✅ Done |

---

## Initiative 2: Ordered Hook Pipeline

| Step | Description | Status |
|------|-------------|--------|
| 2.1 | Add `default int order() { return 0; }` to `AgentHook` interface | ✅ Done |
| 2.2 | Update `HookRegistry` to sort hooks by `order()` in `triggerBeforeAgent`, `triggerAfterAgent`, `triggerBeforeModelCall`, `triggerAfterModelCall`, `triggerBeforeToolCall`, `triggerAfterToolCall` | ✅ Done |
| 2.3 | Assign explicit orders to all existing AgentHook implementations | ✅ Done |

---

## Initiative 3: Package-by-Feature Reorganization

| Step | Description | Status |
|------|-------------|--------|
| 3.1 | Create `features/` package hierarchy under `strands-agents/src/main/java/.../` | ✅ Done |
| 3.2 | Move guardrail code → `features/guardrails/` | ✅ Done |
| 3.3 | Move HITL code → `features/hitl/` | ✅ Done |
| 3.4 | Move skills code → `features/skills/` | ✅ Done |
| 3.5 | Move MCP code → `features/mcp/` | ✅ Done |
| 3.6 | Move resilience code → `features/resilience/` | ✅ Done |
| 3.7 | Move telemetry code → `features/telemetry/` | ✅ Done |
| 3.8 | Move secret providers → `features/secrets/` | ✅ Done |
| 3.9 | Move sessions → `features/sessions/` | ✅ Done |
| 3.10 | Move structured output → `features/structured/` | ✅ Done |
| 3.11 | Move conversation → `features/conversation/` | ✅ Done |
| 3.12 | Move context → `features/context/` | ✅ Done |
| 3.13 | Move pipeline (hooks) → `features/pipeline/` | ✅ Done |
| 3.14 | Move plugin → `features/plugin/` | ✅ Done |
| 3.15 | Move gate → `features/gate/` | ✅ Done |
| 3.16 | Move internal → `features/internal/` | ✅ Done |
| 3.17 | Move planning → `features/planning/` | ✅ Done |
| 3.18 | Move routing → `features/routing/` | ✅ Done |
| 3.19 | Move subagent → `features/subagent/` | ✅ Done |
| 3.20 | Move swarm → `features/swarm/` | ✅ Done |
| 3.21 | Move tools → `features/tools/` | ✅ Done |
| 3.22 | Move workflow → `features/workflow/` | ✅ Done |
| 3.23 | Move security → `features/security/` | ✅ Done |
| 3.24 | Move service → `features/service/` | ✅ Done |
| 3.25 | Move base model classes → `model/` | ✅ Done |
| 3.26 | Move config classes → `config/` | ✅ Done |
| 3.27 | Move prompt classes → `prompt/` | ✅ Done |
| 3.28 | Move remaining core (Agent, AgentFactory, etc.) → flatten into `core/` | ✅ Done |
| 3.29 | Update all imports across `strands-agents`, `strands-agents-quarkus`, `strands-agents-examples` | ✅ Done |
| 3.30 | Move test files to matching feature packages | ✅ Done |
| 3.31 | Verify compilation & all 340 tests pass | ✅ Done |

### Resulting package layout
```
de.augmentia.strandsagents
  ├── core/          ── Agent, AgentFactory, ToolRegistry, ToolExecutor, StreamingAgent, etc.
  ├── config/        ── AgentConfig, ModelFactory, ModelProvider, etc.
  ├── model/
  │   ├── agent/     ── AgentResult, AgentState, StopReason, etc.
  │   ├── api/       ── ChatRequest, ToolInfo, etc.
  │   ├── event/     ── AgentEvent, AgentStartedEvent, AgentFinishedEvent, etc.
  │   ├── message/   ── Message, UserMessage, AssistantMessage, etc.
  │   ├── session/   ── Session
  │   └── tool/      ── ToolCall, ToolExecutionResult
  ├── prompt/        ── PromptManager, PromptRegistry, etc.
  └── features/
      ├── context/      ├── conversation/   ├── gate/        ├── guardrails/
      ├── hitl/         ├── internal/       ├── mcp/         ├── pipeline/
      ├── planning/     ├── plugin/         ├── resilience/  ├── routing/
      ├── secrets/      ├── security/       ├── service/     ├── sessions/
      ├── skills/       ├── structured/     ├── subagent/    ├── swarm/
      ├── telemetry/    ├── tools/          └── workflow/
```

---

## Initiative 4: Feature Toggles

| Step | Description | Status |
|------|-------------|--------|
| 4.1 | Create `features.yaml` with default toggle states | ✅ Done |
| 4.2 | Create `FeatureConfig.java` with Jackson YAML loading | ✅ Done |
| 4.3 | Integrate into `StrandsAgentConfig.fromMixed()` via `fromYaml()` factory | ✅ Done |

YAML defaults are loaded as the base layer, overridden by env vars, then system properties.
Feature config is accessible via `FeatureConfig.load().isEnabled("feature_name")`.

---

## Initiative 5: SPI Modules

| Step | Description | Status |
|------|-------------|--------|
| 5.1 | Create `SecretProviderFactory` SPI interface in core | ✅ Done |
| 5.2 | Create `strands-secret-provider-aws` Maven module | ✅ Done |
| 5.3 | Create `strands-secret-provider-gcp` Maven module | ✅ Done |
| 5.4 | Update `CloudSecretProviderFactory` to use `ServiceLoader<SecretProviderFactory>` | ✅ Done |
| 5.5 | Register modules in parent POM + add dependencies to quarkus module | ✅ Done |
| 5.6 | Move `AwsSsmProvider` + `GcpSecretManagerProvider` from core to SPI modules | ✅ Done |

Cloud providers are now discovered via `ServiceLoader`. Each SPI module implements
`SecretProviderFactory` and registers via `META-INF/services/`. The quarkus module
depends on both SPI modules for backward compatibility. To add a new cloud provider,
create a new Maven module implementing `SecretProviderFactory` and add it to the
classpath. Verification:

```bash
mvn compile -q
mvn test -pl strands-agents -q  # 340/340 pass
mvn test -pl strands-secret-provider-aws,strands-secret-provider-gcp -q  # SPI modules OK
```

---

## Legend

| Status | Meaning |
|--------|---------|
| ✅ Done | Completed and verified |
| 🔄 In Progress | Currently being worked on |
| ⏸️ Blocked | Waiting on dependency or decision |
| Pending | Not yet started |
| 🔮 Future | Planned but not scheduled |
