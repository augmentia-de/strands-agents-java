# Strands Agents — Besondere Features (Quarkus Variante)

Das Framework erweitert LangChain4j um einen vollständigen, produktionsnahen Agenten-Loop mit Multi-Tier-Modell-Routing, Plugin-System, Human-in-the-Loop, Resilience, Skill-Management, MCP-Integration, verschlüsseltem Key-Vault, A2A-Protokoll und umfangreicher Telemetrie.

---

## 1. Multi-Tier Model Routing

**Simple / Advanced / Routing** — drei Betriebsmodi, pro Agent-Session wählbar.

- **Simple Tier:** günstiges/schnelles Modell (z.B. `gpt-4o-mini`)
- **Advanced Tier:** leistungsstarkes Modell (z.B. `gpt-4o`)
- **Routing Tier:** der Simple-Modell klassifiziert den User-Goal und schaltet automatisch auf Advanced um, wenn komplexe Reasoning-Schritte erkannt werden
- `Agent.switchTier()` und `Agent.setModelTier()` erlauben dynamischen Wechsel zur Laufzeit
- Konfiguration pro Tier über `SIMPLE_*` / `ADVANCED_*` Umgebungsvariablen (Provider, Model, Base-URL, API-Key, Temperatur)
- Fallback-Chain: `SIMPLE_*` → `OPENAI_*` → Vault → Fehler

Relevante Dateien:
- `core/config/ModelTier.java`
- `core/config/TieredModelConfig.java`
- `core/config/ChatModelConfig.java`
- `core/config/ModelFactory.java`
- `core/agent/RoutingAgent.java`

---

## 2. Eigenständiger Agent-Loop

Kein `AiServices` — vollständig manuell gesteuerter Loop:

```
IDLE → THINKING → OBSERVING → ACTING → ERROR → FINISHED
```

- **StopReason-Enum:** SUCCESS, TOOL_LIMIT, MAX_ITERATIONS, ERROR, CANCELLED, HITL_REJECTED, GUARDRAIL_BLOCKED, TOOL_FAILURE
- **ExecutionMetrics:** Model-Calls, Tool-Calls, Dauer, Token-Verbrauch (Prompt + Completion)
- **TokenRecovery:** Automatische Wiederherstellung bei Context-Length-Exceeded — kürzt Nachrichtenhistorie und wiederholt den Aufruf
- **AgentState:** Unveränderlicher Snapshot (sessionId, messages, variables, status, phase, metrics, stopReason)

Relevante Dateien:
- `core/agent/Agent.java`
- `core/agent/StreamingAgent.java`
- `core/resilience/TokenRecovery.java`

---

## 3. Plugin-System

Leichtgewichtiges Extension-System für querschnittliche Funktionalität:

| Plugin | Aufgabe |
|---|---|
| `GuardrailPlugin` | Input/Output-Content-Safety (BLOCK, FALLBACK, THROW) |
| `HITLPlugin` | Human-in-the-Loop für bestimmte Tools |
| `AgentSkillsPlugin` | Dynamische Injektion von Skill-Instruktionen in den System-Prompt |

Alle Plugins durchlaufen den Agent-Lifecycle: `beforeAgent()` / `afterAgent()`.

Relevante Dateien:
- `core/plugin/Plugin.java`
- `core/plugin/guardrail/GuardrailPlugin.java`
- `core/plugin/hitl/HITLPlugin.java`
- `skills/AgentSkillsPlugin.java`

---

## 4. Hook-System (6 Lifecycle-Punkte)

Feingranulare Interception mit Fehlerisolierung:

| Hook | Kontext | Rückgabe |
|---|---|---|
| `beforeAgent` | System-Prompt, Session-ID | `Continue`, `Cancel`, `Modify` |
| `afterAgent` | Result, Metriken | `Continue`, `Cancel` |
| `beforeModelCall` | Messages, Model | `Continue`, `Cancel`, `Modify`, `Retry` |
| `afterModelCall` | Response, Dauer | `Continue`, `Cancel`, `Modify` |
| `beforeToolCall` | Tool-Name, Argumente | `Continue`, `Cancel`, `Modify`, `Retry` |
| `afterToolCall` | Result, Dauer, Error | `Continue`, `Cancel` |

**FailurePolicy:** Jeder Hook kann `ISOLATE` (Fehler betrifft nur diesen Hook) oder `ABORT` (Fehler bricht alle Hooks ab).

Relevante Dateien:
- `core/hook/HookRegistry.java`
- `core/hook/AgentHook.java`
- `core/hook/HookContexts.java`

---

## 5. Human-in-the-Loop (HITL)

Checkpoint-basierte Unterbrechung für menschliche Freigabe:

- **CheckpointService** verwaltet PENDING / APPROVED / REJECTED Zustände
- **CheckpointChannel** für Benachrichtigung: SSEChannel (Web), ConsoleChannel (stdout)
- **CheckpointHook** unterbricht den Agent-Loop vor Tool-Ausführung, wartet auf `CompletableFuture<Checkpoint>`
- Timeout-konfigurierbar (default 120s)
- Tool-Filter: `strands.agent.hitl.tools` definiert, welche Tools zustimmungspflichtig sind

Relevante Dateien:
- `core/plugin/hitl/checkpoint/Checkpoint.java`
- `core/plugin/hitl/checkpoint/CheckpointService.java`
- `core/plugin/hitl/checkpoint/CheckpointHook.java`
- `core/plugin/hitl/checkpoint/SSEChannel.java`

---

## 6. Tool-System (18+ Built-in Tools)

Jedes Tool implementiert `AgentTool` — keine `@Tool`-Annotationen:

| Tool | Beschreibung |
|---|---|
| `BashTool` | Shell-Kommandos mit Workspace-Sandbox |
| `ReadTool`, `WriteTool`, `EditTool` | Datei-I/O mit Path-Traversal-Schutz |
| `GrepTool`, `FindTool`, `LsTool` | Dateisuche |
| `HttpTool` | HTTP mit Private-IP-Sperre |
| `WebFetchTool`, `WebSearchTool` | Webzugriff (Tavily) |
| `CalculatorTool`, `TimeTool` | Utilities |
| `DockerRunTool` | Container-Sandbox für Codeausführung |
| `HumanInTheLoopTool` | Explizite menschliche Freigabe |
| `McpToolMethod` | Bridge zu MCP-Server-Tools |
| `CapabilitySearchTool` | Runtime-Skill-Suche |
| `ListToolsTool` | Demo-Tool zur Tool-Reflexion |

Besonderheiten:
- **WorkspacePaths** doppelte Canonicalize-Prüfung verhindert Symlink-Escape
- **AbortFlag** für kooperatives Cancelling langer Tool-Läufe
- **Chaos Engineering:** `RANDOM_TOOL_ERRORS_ENABLED` injiziert zufällig Timeouts, Exceptions oder invalides JSON
- **ToolRegistry** mit fluent Builder, MCP-Präfix-Unterstützung und `@Tool`-Wrapper (`AnnotatedToolMethod` vs `McpToolMethod`)
- `ToolExecutor` nutzt Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`)

Relevante Dateien:
- `core/tools/AgentTool.java`
- `core/ToolRegistry.java`
- `core/ToolExecutor.java`
- `core/internal/WorkspacePaths.java`

---

## 7. Conversation Management

Zwei Strategien zur Begrenzung des Context-Windows:

| Strategie | Verhalten |
|---|---|
| `SlidingWindowConversationManager` | Behält die letzten N Messages |
| `SummarizingConversationManager` | Fasst ältere Messages per LLM in einer Zusammenfassung zusammen |

Beide implementieren das `ConversationManager`-Interface.

Relevante Dateien:
- `core/conversation/ConversationManager.java`
- `core/conversation/SlidingWindowConversationManager.java`
- `core/conversation/SummarizingConversationManager.java`

---

## 8. Session Management

Persistente Sessions über Agent-Restarts hinweg:

| Implementierung | Speicher |
|---|---|
| `FileSessionManager` | JSON-Dateien mit FileLock |
| `JdbcSessionManager` | Relationale Datenbank (Auto-Schema) |

Relevante Dateien:
- `sessions/SessionManager.java`
- `sessions/FileSessionManager.java`
- `sessions/JdbcSessionManager.java`

---

## 9. Resilience

| Komponente | Beschreibung |
|---|---|
| **Retry** | Konfigurierbare Wiederholung mit Backoff; Auth-Fehler (401/403) werden NICHT wiederholt |
| **CircuitBreaker** | Drei Zustände: CLOSED → OPEN → HALF_OPEN; Fallback-Action bei offenem Circuit |
| **TokenRecovery** | Automatische Nachrichtenkürzung bei Context-Length-Exceeded |

Relevante Dateien:
- `core/resilience/Retry.java`
- `core/resilience/CircuitBreaker.java`
- `core/resilience/TokenRecovery.java`
- `core/resilience/ResilienceConfig.java`

---

## 10. Skills / Capabilities

Skills werden aus Markdown-Dateien mit YAML-Frontmatter geladen:

```yaml
---
name: code-review
description: Review code for bugs and style issues
allowedTools: [ReadTool, GrepTool]
---
Instructions for the agent...
```

- **SkillParser** parst `.md`-Dateien aus dem Skills-Verzeichnis
- **CapabilityRegistry** sammelt Skills aus Dateisystem und MCP-Servern
- **AgentSkillsPlugin** injiziert max. 3 initiale Skills als XML in den System-Prompt
- **CapabilitySearchTool** erlaubt LLM-gesteuerte Runtime-Skill-Suche

Relevante Dateien:
- `skills/SkillParser.java`
- `skills/Skill.java`
- `skills/CapabilityRegistry.java`
- `skills/AgentSkillsPlugin.java`
- `skills/CapabilitySearchTool.java`

---

## 11. MCP (Model Context Protocol)

Multi-Server-Unterstützung mit Namensraum-Isolation:

- **Transports:** SSE (Server-Sent Events), StreamableHTTP
- **Tool-Präfix:** `<serverName>_<toolName>` verhindert Kollisionen
- **McpIngestTool:** Agent kann zur Laufzeit neue MCP-Server verbinden
- **McpListTool:** Listet aktuell verbundene Server und deren Tools
- **McpServerConfigLoader** lädt Konfiguration aus `config/MCP_SERVER_CONFIG.json`

Relevante Dateien:
- `core/mcp/McpConnector.java`
- `core/tools/McpToolMethod.java`
- `skills/McpIngestTool.java`
- `skills/McpListTool.java`
- `skills/McpServerConfigLoader.java`

---

## 12. Prompt Management

Alle LLM-Prompts (29 Keys) sind in einer zentralen YAML-Datei externalisiert:

```yaml
prompts:
  routing_agent.classifier: "Classify the user's goal..."
  agent.llm_error: "Error: %s"
```

- **YamlPromptManager:** Lädt aus Classpath + optionale Override-Dateien
- **CompositePromptManager:** Chain-of-Responsibility (Override-Dir → Classpath → Redis-Extension)
- **PromptRegistry:** Globaler Singleton mit lazy-init, `get(key, args...)` für String-Formatierung

Relevante Dateien:
- `resources/prompts.yaml`
- `core/prompt/PromptRegistry.java`
- `core/prompt/PromptManager.java`
- `core/prompt/YamlPromptManager.java`
- `core/prompt/CompositePromptManager.java`

---

## 13. Secrets / Vault

| Mechanismus | Beschreibung |
|---|---|
| **Umgebungsvariablen** | `OPENAI_API_KEY`, `SIMPLE_API_KEY`, etc. |
| **Hashicorp Vault** | Auto-Detection KV v1/v2, Token-Auth |
| **AES-256/GCM Key Vault** | PBKDF2-100k Iterationen, Passwort-geschützt, Datei `api-key.enc` |
| **Runtime API Key** | Quick-Setup-UI setzt Key im laufenden Betrieb |
| **CompositeSecretProvider** | Fallback-Kette: Vault → File → Env |

Das Quarkus-UI erlaubt Speichern und Aktivieren von API-Keys über `/api/vault/*` Endpunkte.

Relevante Dateien:
- `vault/SecretProvider.java`
- `vault/VaultSecretProvider.java`
- `vault/CompositeSecretProvider.java`
- `quarkus/service/ApiKeyVault.java`
- `quarkus/service/KeyVaultHolder.java`
- `quarkus/service/SecretService.java`

---

## 14. A2A (Agent-to-Agent Protocol)

Standardkonforme A2A-Exposition:

- **SubAgentExecutor:** Parallele Sub-Agent-Ausführung via Virtual Threads
- **SubAgentTool:** Agenten-Tool zur Delegation mit Rekursionsschutz (max. 5 Ebenen)
- **StrandsA2AProducers:** CDI-Bean-Erzeugung für `AgentCard` und `AgentExecutor`

Relevante Dateien:
- `core/agent/subagent/SubAgentExecutor.java`
- `core/agent/subagent/SubAgentTool.java`
- `quarkus/a2a/StrandsA2AProducers.java`

---

## 15. Planning / Chain-of-Thought

Strukturierte Plan-Erstellung vor Ausführung:

- **CoTPlanner** erstellt JSON-Plan `{ "steps": [...] }` mit Tool-Zuordnung
- Bis zu 3 Revisions-Runden bei Validierungsfehlern
- **PlanningAgent** führt Plan Schritt-für-Schritt mit `CURRENT_STEP`-Kontext-Variable aus

Relevante Dateien:
- `core/agent/planning/Planner.java`
- `core/agent/planning/CoTPlanner.java`
- `core/agent/planning/PlanningAgent.java`
- `core/agent/planning/Plan.java`
- `core/agent/planning/Step.java`
- `core/agent/planning/StepResult.java`

---

## 16. Telemetrie / Observability

| Komponente | Technologie | Erfasst |
|---|---|---|
| **AgentEvent-System** | Hierarchische Events | Start/Stop, Model-Calls, Tool-Calls, Tokens, State-Transitions, Errors |
| **OpenTelemetry Tracing** | Spans | Agent-Run, Model-Call, Tool-Execution, annotiert mit Session-ID |
| **Micrometer Metrics** | Counters + Timers | Model-Calls, Tool-Calls, Errors, Token-Verbrauch |
| **FileLlmLogger** | Rotierende Dateien (2MB × 10) | Alle LLM-Requests/Responses mit Timestamp |

Relevante Dateien:
- `telemetry/AgentEvent.java` (+ Subklassen)
- `telemetry/AgentEventListener.java`
- `telemetry/AgentTracing.java`
- `telemetry/AgentMetrics.java`
- `core/logging/FileLlmLogger.java`
- `core/logging/LoggingChatModel.java`

---

## 17. Structured Output

- **Zwei Modi:** `STATIC` (vordefiniertes Schema) und `DYNAMIC` (LLM-generiertes Schema)
- **Force-Prompt-Fallback:** Falls das Modell kein natives Structured Output unterstützt, wird "Respond in JSON" an den Prompt angehängt
- Unterstützt OpenAI `response_format` Parameter

Relevante Dateien:
- `core/structured/StructuredOutputConfig.java`

---

## 18. Quarkus REST API

| Endpunkt | Methode | Beschreibung |
|---|---|---|
| `/api/chat` | POST | Synchrone Chat-Anfrage |
| `/api/chat/stream` | POST (SSE) | Streaming Chat via Server-Sent Events |
| `/api/agent/init` | POST | Agent initialisieren mit Tool/Skill/MCP-Auswahl |
| `/api/agent/reinit` | POST | Agent neu initialisieren (bestehende Session) |
| `/api/agent/release` | POST | Session freigeben |
| `/api/tools` | GET | Registrierte Tools auflisten |
| `/api/skills` | GET | Geladene Skills auflisten |
| `/api/sessions` | GET | Aktive Sessions auflisten |
| `/api/mcp/discover` | POST | MCP-Server-Tools entdecken |
| `/api/mcp/connect` | POST | Benutzerdefinierten MCP-Server verbinden |
| `/api/mcp/servers` | GET | Konfigurierte MCP-Server auflisten |
| `/api/admin/setup` | POST | API-Key + Passwort speichern (AES-Vault) |
| `/api/admin/activate` | POST | API-Key aktivieren |
| `/api/admin/deactivate` | POST | API-Key deaktivieren |
| `/api/admin/status` | GET | Vault-Status abfragen |
| `/api/vault/status` | GET | Key-Vault-Status |
| `/api/vault/authenticate` | POST | Key-Vault entsperren |
| `/api/vault/write` | POST | Key speichern/löschen |
| `/api/vault/reload` | POST | Alle Keys in System-Properties laden |
| `/q/swagger-ui` | GET | OpenAPI-Dokumentation |
| `/q/health` | GET | Health-Check |

Relevante Dateien:
- `quarkus/resources/ChatResource.java`
- `quarkus/resources/SessionResource.java`
- `quarkus/resources/AdminResource.java`
- `quarkus/resources/KeyVaultResource.java`
- `quarkus/resources/ToolResource.java`
- `quarkus/resources/UiResource.java`

---

## 19. CapabilityToken (Sicherheit)

Feingranulare Zugriffskontrolle pro Tool:

- `FILE_READ`, `FILE_WRITE`, `FILE_DELETE`
- `DB_READ`, `DB_WRITE`
- `NETWORK`, `EXECUTE`, `DOCKER`
- `MCP`, `WEB_SEARCH`, `WEB_FETCH`, `ADMIN`

Jedes Tool deklariert benötigte Tokens; der Agent prüft gegen gewährte Berechtigungen.

Relevante Dateien:
- `core/security/CapabilityToken.java`
- `core/security/Gate.java`

---

## 20. Workflow / Orchestrierung

Multi-Step-Workflow-Engine für komplexe Prozesse:

- **WorkCoordinator:** Dispatch + Collect Pattern
- **WorkflowDefinition:** Schritte mit Rollen, Input/Output-Mapping, Next-Step-Logik
- **StepStatus:** PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, WAITING_FOR_HUMAN

Relevante Dateien:
- `core/workflow/WorkCoordinator.java`
- `core/workflow/WorkflowDefinition.java`
- `core/workflow/WorkflowStep.java`
- `core/workflow/StepStatus.java`

---

## 21. CapabilitySearchTool

**Package:** `skills.CapabilitySearchTool` (not `core.tools`)

Ein spezialisierter Sub-Agent zur Runtime-Erkennung von Skills und MCP-Tools:

- **Parameter:** `task` (AI-powered Analyse über alle Quellen) oder `query` (Keyword-Filter)
- **Sub-Agent:** Erzeugt einen eigenen Agenten mit `SkillSearchTool` + `McpListTool` zur LLM-gesteuerten Analyse
- **CapabilityRegistry** aggregiert aus zwei Quellen:
    - **Skill-Directories** (`.md`-Dateien mit YAML-Frontmatter, via `SkillParser`)
    - **MCP-Server** (live verbundene Server, via `McpClient.listTools()`)
- **CapabilityTypes:** `SKILL`, `MCP_TOOL`

Der Sub-Agent führt die Analyse durch, das Ergebnis wird als strukturierte Empfehlung + vollständige Capability-Liste zurückgegeben.

Relevante Dateien:
- `skills/CapabilitySearchTool.java`
- `skills/CapabilityRegistry.java`
- `skills/SkillSearchTool.java`
- `skills/McpListTool.java`

---

## 22. Runtime Switching während einer laufenden Konversation

### 22.1 Tools

```java
agent.addTool(new CalculatorTool());            // AgentTool<?> oder @Tool-instance
agent.removeTool("BashTool");
agent.setToolRegistry(anEntirelyNewRegistry());  // kompletter Austausch
// Änderungen wirken beim nächsten LLM-Call
```

`Agent.java:996-1009` — Methoden existieren, Änderungen werden beim nächsten Iterations-Durchlauf wirksam (toolRegistry ist eine volatile/veränderbare Referenz).

### 22.2 Hooks

```java
agent.addHook(new MyCustomHook());
agent.removeHook("hookName");
agent.setHookRegistry(new HookRegistry());
```

`Agent.java:1016-1026` — Hooks können zur Laufzeit hinzugefügt/entfernt werden. Der HookRegistry wird pro Iteration abgefragt.

### 22.3 System Prompt

```java
agent.setSystemPrompt("New system instructions");
```

`Agent.java:353-355` — `systemPrompt` ist ein einfaches String-Feld, das beim nächsten `buildRequest()` verwendet wird. **Kein setter im fluent-builder-Stil** — direktes Setzen.

### 22.4 Model (Multi-Tier)

```java
agent.switchTier(ModelTier.ADVANCED);           // wechselt das aktive Model
agent.setModelTier(ModelTier.SIMPLE);
agent.setAdvancedModel(newAdvancedModel);        // advanced Model austauschen
agent.setSimpleModel(newSimpleModel);            // simple Model austauschen
```

`Agent.java:1096-1126` — `getCurrentModel()` prüft `currentTier` und gibt das entsprechende Model zurück. Wird beim nächsten LLM-Call verwendet.

**RoutingAgent** (`RoutingAgent.java:44-82`):
```java
RoutingAgent ra = new RoutingAgent(simpleModel, advancedModel, ...);
ra.resolveRoutingTier(userGoal);   // klassifiziert den Goal per LLM → SIMPLE/ADVANCED
ra.applyRouting();                 // wendet den resolved Tier an
```

### 22.5 Plugin-Nachregistrierung

Plugin-Änderungen sind **nicht** zur Laufzeit vorgesehen — Plugins werden im Constructor registriert und via `PluginRegistry` initialisiert. Ein nachträgliches Hinzufügen erfordert direkten Zugriff auf `plugins`-Liste und erneute Initialisierung.

### 22.6 Zusammenfassung

| Komponente | Runtime-Switch | Setter | Wirkung |
|---|---|---|---|
| Tools | ✅ | `addTool`, `removeTool`, `setToolRegistry` | Nächster LLM-Call |
| Hooks | ✅ | `addHook`, `removeHook`, `setHookRegistry` | Nächster Iterations-Schritt |
| System Prompt | ✅ | `setSystemPrompt` | Nächster LLM-Call |
| Model (Tier) | ✅ | `switchTier`, `setModelTier`, `setAdvancedModel` | Nächster LLM-Call |
| Plugins | ❌ | Kein public setter | Nur via Konstruktor |
| Skills | ⚠️ | `AgentSkillsPlugin.activateSkill()` | Prompt-Injektion nächster Call |
| ConversationManager | ❌ | Kein setter | Nur via Konstruktor |
| SessionManager | ❌ | Kein setter | Nur via Konstruktor |

---

## 23. Modern Workflows & Integration Patterns

### 23.1 Coding Agent Workflow

Typische Tool-Kombination für Code-Generierung und -Bearbeitung:

```java
var tools = ToolRegistry.builder()
    .standard(false)           // Read, Write, Edit, Find, Grep, Ls, WebFetch, WebSearch, DockerRun
    .with(new HttpTool())      // API-Integration
    .exclude("WebSearchTool")  // nur lesend im Workspace
    .build();

agent.setSystemPrompt("You are a coding agent. Read files, understand the codebase, "
    + "then make targeted edits. Verify your changes by reading the edited files.");
```

**Patterns:**
- **Read-Edit-Read:** File lesen → editieren → erneut lesen zur Verifikation
- **Multi-File-Analyse:** Grep/Find für Querverweise, dann Read für Details
- **Docker-Sandbox:** DockerRunTool isolierte Code-Ausführung

### 23.2 Research Agent Workflow

```java
var tools = ToolRegistry.builder()
    .standard(false)
    .exclude("BashTool", "DockerRunTool", "EditTool", "WriteTool")
    .with(new HttpTool())
    .with(new CalculatorTool())
    .with(new TimeTool())
    .build();

agent.setSystemPrompt("You are a research agent. Use web_search and web_fetch "
    + "to gather information. Cite sources. Summarize findings.");
```

**Patterns:**
- **Search-Fetch-Extract:** Web-Suche → Seite fetchen → relevante Informationen extrahieren
- **Multi-Source:** Mehrere Quellen parallel abfragen und Ergebnisse konsolidieren
- **Structured Output:** Ergebnisse in JSON-Schema extrahieren

### 23.3 Capability-Driven Dynamic Workflow

Mit `CapabilitySearchTool` und Skill-System:

```java
var capRegistry = CapabilityRegistry.builder()
    .skillDir(Path.of("skills/"))
    .mcpServer("db", "http://localhost:3001/sse")
    .build();

var tools = ToolRegistry.builder()
    .standard(false)
    .exclude("BashTool")
    .with(new CapabilitySearchTool(capRegistry, model))
    .build();

agent.setSystemPrompt("Use capability_search to discover tools and skills "
    + "relevant to your task before starting.");
```

Der LLM kann selbstständig die verfügbaren Fähigkeiten entdecken und zielgerichtet einsetzen — besonders nützlich bei wechselnden Aufgabenprofilen.

### 23.4 Multi-Agent Orchestrierung

```java
// Agent A: Koordinator mit SubAgentTool delegiert an Spezialisten
var specialistTools = ToolRegistry.builder()
    .standard(false).include("ReadTool", "WriteTool", "EditTool", "GrepTool")
    .build();

var specialist = new Agent(specialistModel, specialistTools, new ToolExecutor());
specialist.setSystemPrompt("You are a code specialist...");

var coordinatorTools = ToolRegistry.builder()
    .standard(false).include("ReadTool", "WebSearchTool")
    .with(new SubAgentTool("code-specialist", specialist))
    .build();
```

**Patterns:**
- **Dispatch-Collect:** Koordinator delegiert Teilaufgaben an Spezialisten
- **Rekursionsschutz:** `SubAgentTool` limitiert auf max. 5 Ebenen
- **Virtuelle Threads:** Parallele Sub-Agent-Ausführung via `SubAgentExecutor`

### 23.5 Guarded Agent Production Pattern

```java
GuardrailPlugin guardrails = new GuardrailPlugin(
    List.of(new PiiGuardrail(), new PromptInjectionGuardrail()),
    List.of(new SensitiveDataGuardrail()),
    BlockAction.FALLBACK, "Inhalt konnte nicht verarbeitet werden."
);

HITLPlugin hitl = new HITLPlugin(
    new ConsoleHITLProvider(),
    HITLAuthority.CONFIRM
);

var hooks = new HookRegistry();
hooks.register(new AuditLogHook());
hooks.register(new RateLimitHook());

var agent = new Agent(model, tools, new ToolExecutor(),
    new SlidingWindowConversationManager(20),
    new FileSessionManager(Path.of(".sessions")),
    ResilienceConfig.DEFAULT,
    List.of(guardrails, hitl),
    hooks);
```

**Empfohlene Guardrail-Reihenfolge (cheapest first):** Rate Limit → Context Length → Prompt Injection

---

## 24. Documentation Gaps: FEATURES.MD ↔ API.MD ↔ AGENT_USE.md ↔ Implementation

### 24.1 features.md Inaccuracies — RESOLVED

| Claim | File | Actual | Status |
|---|---|---|---|
| `CapabilitySearchTool` in `core/tools/` | Line 134 (old) | `skills/CapabilitySearchTool.java` | Notiert |

### 24.2 API.md Inaccuracies — RESOLVED

| Claim | Section | Actual | Status |
|---|---|---|---|
| `RoutingAgent(...)` | 2.3 | Korrigiert (kein LlmRouter) | FIXED |
| Event types | 4.7 | Namen korrigiert | FIXED |

### 24.3 AGENT_USE.md Inaccuracies — RESOLVED

| Claim | Section | Actual | Status |
|---|---|---|---|
| `agent.setStructuredOutputModel()` | 9.1 | Methode EXISTIERT in Agent.java | INVALID CLAIM |
| `.standard()` tool table | 3.1 | `DockerRunTool` hinzugefügt | FIXED |
| Event types | 10.3 | Namen korrigiert | FIXED |


### 24.4 Missing Documentation (in allen Docs)

- **Runtime Switching** von Tools/Hooks/Prompt/Model ist nur in AGENT_USE.md §3.2 angedeutet
- **Dynamic Tool Injection via Hooks** — AGENT_USE.md §14.6 hat gutes Beispiel, features.md fehlt komplett
- **CapabilitySearchTool Sub-Agent Mechanismus** — nirgends dokumentiert wie der Sub-Agent funktioniert
- **Rekursionsschutz** in SubAgentTool — nur API.md §22 erwähnt "max 5 levels deep"
- **Chaos Engineering** (`RANDOM_TOOL_ERRORS_ENABLED`) — nur in features.md §6 erwähnt

---

## 25. Offene / Unklare Features

| Feature | Status | Anmerkung |
|---|---|---|
| **LlmRouter** | §2.3 (API) | Klasse existiert, wird aber von `RoutingAgent` noch nicht genutzt |
| **GuardrailHook** | §5.3 (API) | Klasse existiert und ist funktionsfähig |
| **TokenEvent** Ausgabe | Telemetrie | Korrekt, wird nur von StreamingAgent gefeuert |
| **`@Tool` Annotation** | §3.2 (API) | Korrekt, Import ist `dev.langchain4j.agent.tool.Tool` |
| **Gate Annotation** | §5.7 (API) | Klasse existiert, wird aber noch nicht im Agent-Loop ausgewertet |

---

## 26. Vorschläge zur Lösung offener Punkte

### 26.1 LlmRouter & RoutingAgent
Der `RoutingAgent` sollte den `LlmRouter` injiziert bekommen, statt die Klassifizierungs-Logik hart codiert in `resolveRoutingTier` zu halten.
- **Plan:** `LlmRouter` als optionalen Parameter in `RoutingAgent` aufnehmen. Falls vorhanden, nutzt `resolveRoutingTier` den Router.

### 26.2 Gate Annotation Support
Die `@Gate` Annotation ist definiert, wird aber ignoriert.
- **Plan:** Ein `GateHook` (Teil eines `GatePlugin`) implementieren, der in `beforeToolCall` prüft, ob die Ziel-Methode des Tools mit `@Gate` annotiert ist. Falls ja, wird der `GateEvaluator` konsultiert.

