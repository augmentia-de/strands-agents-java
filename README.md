# Strands Agents SDK – Java 21 Port

A Java 21 port of the [Strands Agents SDK (TypeScript)](https://github.com/strands-agents), built on **LangChain4j** and optionally served via **Quarkus**.

> **Status:** Experimental. Personal project — large parts may be untested, the API is unstable and will change without notice.

---

## Development Status

This is an **early, experimental** codebase. It compiles and basic flows work, but:

- **Most features are untested** — only a few integration tests exist, many code paths are never exercised
- **The API is unstable** — classes, method signatures, and package structure change frequently without notice
- **Error handling is incomplete** — many edge cases throw cryptic exceptions or silently fail
- **Thread safety is not guaranteed** — concurrent access corner cases are not addressed
- **Documentation is sparse** — reading the source is the best reference

The codebase includes first-pass implementations of:

- Agent loop with tool-calling, multi-turn conversation, streaming, planning, swarm
- ~17 built-in tools (bash, read/write/edit files, web fetch/search, HTTP, calculator, MCP bridge, etc.)
- Resilience primitives (retry, circuit breaker, token-limit recovery)
- Conversation & session management (sliding window, summarising, file/JDBC persistence)
- Plugin system (guardrails, human-in-the-loop)
- A2A sub-agent delegation and LLM-based routing
- Skill discovery from markdown files and MCP capability registry
- MCP client (stdio/SSE transport)
- OpenTelemetry tracing and Micrometer metrics
- Secret providers (HashiCorp Vault, file, composite)
- Structured output (JSON schema)
- Quarkus REST API module (chat, SSE streaming, session/tool/skill management)

**None of these are production-ready.** Most are draft-quality with known gaps.

What is **not implemented at all**:
- Kafka event streaming
- Vector database / RAG pipeline
- gRPC inter-agent communication
- Redis caching / pub-sub

---

## Prerequisites

- Java 21 (JDK) with `--enable-preview`
- Maven 3.9+
- Optional: OpenAI-compatible API key (`OPENAI_API_KEY`, `SIMPLE_API_KEY`, or via Vault)

---

## Quick Start

```bash
# Build project and run tests
./dev.sh test

# With a real LLM:
export OPENAI_API_KEY=sk-...
mvn test -pl strands-agents -Dtest=AgentMvpIT

# Run the mock demo (no API key needed)
./dev.sh run-mock
```

---

## Usage Examples

### Programmatic API (standalone)

```java
// 1. Create an LLM (reads OPENAI_API_KEY, OPENAI_MODEL from env)
ChatModel model = ModelFactory.createOpenAiFromEnv();

// 2. Register tools
var registry = new ToolRegistry();
registry.register(new CalculatorTool());
registry.register(new WebSearchTool());

// 3. Create the agent
var agent = new Agent(model, registry, new ToolExecutor());

// 4. Observe events (optional)
agent.setEventListener(event -> System.out.println("Event: " + event));

// 5. Execute a prompt
AgentResult result = agent.execute("What is 3 + 4? Search the web for current news.");
System.out.println(result.finalAnswer());
```

### With Mock LLM (no API key required)

```java
var agent = new Agent(new MockChatModel());
var result = agent.execute("Hello world");
System.out.println(result.finalAnswer());
```

### With resilience, conversation, and plugins

```java
// Conversation management (sliding window of 10 turns)
ConversationManager cm = new SlidingWindowConversationManager(10);

// Session persistence (file-based)
SessionManager sm = new FileSessionManager(Path.of(".sessions"));

// Resilience config
ResilienceConfig rc = new ResilienceConfig(
    new RetryConfig(3, 1000, 2.0),
    new CircuitBreakerConfig(0.5f, 10L, 30L)
);

// Plugins
List<Plugin> plugins = List.of(
    new GuardrailPlugin(inputGuardrails, outputGuardrails),
    new HITLPlugin(hitlProvider, HITLAuthority.CONFIRM)
);

var agent = new Agent(model, registry, executor, cm, sm, rc, plugins);
agent.setSystemPrompt("You are a helpful and secure assistant.");
AgentResult result = agent.execute("List files in the current directory.");
```

### Quarkus REST API

The `strands-agents-quarkus` module provides a full REST API for the agent.

```bash
# Start the Quarkus dev server
./start-quarkus.sh
# Server starts on http://localhost:8082
```

**Available endpoints:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/chat` | POST | Send a prompt, get a response |
| `/api/chat/stream` | POST (SSE) | Stream response tokens via Server-Sent Events |
| `/api/agent/init` | POST | Initialise a persistent agent session with tool/skill selection |
| `/api/agent/release` | POST | Release an initialised session |
| `/api/tools` | GET | List all registered tools |
| `/api/skills` | GET | List all loaded skills |
| `/api/mcp/discover` | POST | Discover tools from an MCP server |
| `/api/sessions` | GET | List active sessions |
| `/q/swagger-ui` | GET | Swagger UI for API exploration |
| `/q/health` | GET | Health check endpoint |

**Example REST call:**

```bash
curl -X POST http://localhost:8082/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 3 + 4?"}'
```

Response:
```json
{
  "answer": "3 + 4 = 7",
  "sessionId": "uuid-here",
  "stopReason": "TOOL_EXECUTION_COMPLETE",
  "durationMs": 1234,
  "inputTokens": 45,
  "outputTokens": 12,
  "toolCalls": 1,
  "phases": ["IDLE\u2192THINKING", "THINKING\u2192TOOL_EXECUTION", "TOOL_EXECUTION\u2192FINISHED"]
}
```

### Streaming (SSE)

```bash
curl -X POST http://localhost:8082/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"prompt": "Tell me a story"}'
```

### Demo applications

Run any of the pre-built demos in `strands-agents-examples`:

| Demo | Description |
|------|-------------|
| `MainMock` | Full agent demo using MockChatModel (no API key) |
| `Main` | Same demo with real OpenAI LLM |
| `ChatCLI` | Interactive REPL chat client in the terminal |
| `CodeAssistantDemo` | Code review assistant agent |
| `MedicalAssistantDemo` | Medical assistant with structured output |
| `SreIncidentResponseDemo` | SRE incident response automation |
| `AutonomousSwarmDemo` | Swarm of autonomous agents |
| `RecursiveThinkingDemo` | Chain-of-thought planning agent |
| `StructuredOutputDemo` | JSON-schema guided output |
| `WorkflowDemo` | Multi-step workflow execution |
| `LibraryAgentDemo` | Library research assistant |
| `ProductivityAssistantDemo` | Productivity agent |

---

## Project Structure

```
strands-agents-java (parent)
├── strands-agents               Core library: agent loop, tool system, resilience,
│                                  conversation/session management, plugins (guardrails,
│                                  HITL), A2A, planning, routing, swarm, skills,
│                                  MCP client, telemetry (OpenTelemetry + Micrometer),
│                                  vault secrets, structured output
│   └── src/main/java/de/augmentia/strandsagents/
│       ├── core/                Agent, tools, events, resilience, config, plugins
│       ├── sessions/            Session persistence (file, JDBC)
│       ├── skills/              Skill parsing, capability registry
│       ├── telemetry/           Tracing, metrics, hooks
│       └── vault/               Secret providers
├── strands-agents-quarkus       Quarkus 3.17 REST API: chat, streaming (SSE),
│                                  session/tool/skill management, Swagger UI
│   └── src/main/java/de/augmentia/strandsagents/quarkus/
│       ├── resources/           REST endpoints (Chat, Session, Tool, UI)
│       ├── service/             AgentService CDI bean
│       ├── dto/                 Request/response DTOs
│       └── a2a/                 A2A CDI producers
├── strands-agents-examples      Demo applications (mock, OpenAI, CLI, swarm, etc.)
├── docker/                      Dockerfile + docker-compose
├── deploy/                      Kubernetes manifests + Helm chart
└── docs/                        Architecture documentation
```

---

## Configuration

### LLM / Model

#### Single-Provider (legacy, falls back to this if no multi-tier is configured)

| Env variable                 | Property (application.properties) | Default                     | Description                          |
|------------------------------|-----------------------------------|-----------------------------|--------------------------------------|
| `OPENAI_API_KEY`             | —                                 | —                           | OpenAI-compatible API key            |
| `OPENAI_BASE_URL`            | —                                 | `https://api.openai.com/v1` | API base URL                         |
| `OPENAI_MODEL`               | —                                 | `gpt-4o`                    | Model name                           |
| `LLM_TEMPERATURE`            | `strands.agent.temperature`       | `0.7`                       | LLM temperature                      |
| `LLM_MAX_RETRIES`            | —                                 | `3`                         | Max retries on LLM call failure      |
| —                            | `strands.agent.max-tool-iterations` | `10`                      | Max tool-call iterations in one turn |

#### Multi-Tier (optional, overrides single-provider)

Each tier can use a different provider. Prefix: `SIMPLE_` / `ADVANCED_`.

| Env variable                 | Description                                     |
|------------------------------|-------------------------------------------------|
| `SIMPLE_PROVIDER`            | Provider for the simple tier (`openai`, `ollama`, `openai-compatible`) |
| `SIMPLE_MODEL`               | Model name for the simple tier (e.g. `gpt-4o-mini`, `llama3`) |
| `SIMPLE_API_KEY`             | API key for simple tier (falls back to `OPENAI_API_KEY`) |
| `SIMPLE_BASE_URL`            | Base URL for simple tier                        |
| `SIMPLE_OLLAMA_BASE_URL`     | Ollama base URL (only for `SIMPLE_PROVIDER=ollama`) |
| `ADVANCED_PROVIDER`          | Provider for the advanced tier                  |
| `ADVANCED_MODEL`             | Model name for the advanced tier (e.g. `gpt-4o`) |
| `ADVANCED_API_KEY`           | API key for advanced tier                       |
| `ADVANCED_BASE_URL`          | Base URL for advanced tier                      |
| `LLM_DEFAULT_TIER`           | `simple`, `advanced`, or `routing`              |

**Fallback chain per tier:**
- SIMPLE: `SIMPLE_*` env vars → `OPENAI_*` env vars → Vault → error
- ADVANCED: `ADVANCED_*` env vars → SIMPLE values → `OPENAI_*` → Vault → error

**Routing tier** (`LLM_DEFAULT_TIER=routing`): at init time, the simple model classifies the user goal and switches to the advanced model if complex reasoning is detected.

### Agent

| Env variable                    | Property (application.properties)              | Default       | Description                              |
|---------------------------------|------------------------------------------------|---------------|------------------------------------------|
| `STRANDS_SKILLS_DIR`            | `strands.agent.skills.dir`                     | `skills`      | Skills directory                         |
| —                               | `strands.agent.skills.initial`                 | —             | Comma-separated skills to activate at startup (max 3) |
| `STRANDS_SKILLS_SEARCH`         | `strands.agent.skills.search`                  | `false`       | Enable LLM-driven skill-search tool      |
| `STRANDS_SESSION_DIR`           | `strands.agent.session.dir`                    | `.sessions`   | Session persistence directory            |
| `STRANDS_LLM_LOG_ENABLED`       | `strands.agent.llm-log.enabled`                | `true`        | Enable LLM call logging                  |
| `STRANDS_LLM_LOG_PATH`          | `strands.agent.llm-log.path`                   | `logs/llm-calls.log` | LLM log file path                  |
| `STRANDS_AGENT_TOOLS`           | `strands.agent.tools`                          | —             | Comma-separated extra tool class names   |
| —                               | `strands.agent.hitl.tools`                     | —             | Tools requiring human-in-the-loop approval |
| `STRANDS_AGENT_BASH_ALLOW`      | `strands.agent.bash.allow`                     | `false`       | Allow `BashTool` execution               |
| `STRANDS_AGENT_HTTP_ALLOW_PRIVATE` | `strands.agent.http.allow-private`          | `false`       | Allow HTTP tool on private IPs           |
| `STRANDS_AGENT_WORKSPACE`       | `strands.agent.workspace`                      | —             | Working directory for file operations    |
| `STRANDS_CAPABILITIES_DIRS`     | `strands.agent.capabilities.dirs`              | —             | Additional capability/skill directories  |
| `STRANDS_MCP_CONFIG`            | `strands.agent.mcp.config`                     | `config/MCP_SERVER_CONFIG.json` | MCP server JSON config path      |
| `STRANDS_MCP_INGEST`            | `strands.agent.mcp.ingest`                     | `false`       | Ingest skills from MCP servers           |

### Vault (HashiCorp Vault)

| Env variable   | Description                                     |
|----------------|-------------------------------------------------|
| `VAULT_ADDR`   | Vault server URL (e.g. `http://localhost:8200`) |
| `VAULT_TOKEN`  | Vault authentication token                      |
| `VAULT_MOUNT_PATH` | Vault secrets mount path (default: `secret`) |

Expected paths: `secret/openai` (key `api_key`), `secret/tavily` (key `api_key`).

### Web Search

| Env variable    | Description                       |
|-----------------|-----------------------------------|
| `TAVILY_API_KEY` | Tavily Search API key (https://tavily.com) |

### API Key Vault (built-in AES-encrypted key store)

| Env variable        | Description                                   |
|---------------------|-----------------------------------------------|
| `JSTRANDS_KEY_PATH` | Path to the encrypted key store JSON file     |

### A2A (Agent-to-Agent Protocol)

| Env variable  | Property    | Default                  | Description                          |
|---------------|-------------|--------------------------|--------------------------------------|
| `AGENT_URL`   | `agent.url` | `http://localhost:8080`   | A2A agent endpoint URL               |

### Prompts (centralised YAML-based prompt management)

All LLM prompts are externalised into `strands-agents/src/main/resources/prompts.yaml`.  
To override prompts at runtime without rebuilding:

| Property (application.properties)       | Description                                      |
|-----------------------------------------|--------------------------------------------------|
| `strands.agent.prompts.override-dir`    | Directory with `.yaml` override files (see below) |

Override files use the same YAML structure and are merged on top of the built-in prompts:

```yaml
prompts:
  routing_agent.classifier: "Your custom classifier prompt..."
  agent.llm_error: "Custom error message: %s"
```

Currently managed prompts (29 keys): `routing_agent.*`, `cot_planner.*`, `llm_router.system`, `structured_output.force_prompt`, `summarizing.*`, `guardrail_plugin.fallback`, `capability_search_tool.system`, `mock_chat_model.*`, `web_search_tool.mock_result`, `logging_chat_model.error_template`, `agent.*`, `agent_service.*`, `agent_skills_plugin.*`.

### Testing / Chaos Engineering

| Env variable                           | Description                                      |
|----------------------------------------|--------------------------------------------------|
| `RANDOM_TOOL_ERRORS_ENABLED`           | Enable random tool failure simulation            |
| `RANDOM_TOOL_TIMEOUT_PROBABILITY`      | Probability of simulated tool timeout            |
| `RANDOM_TOOL_EXCEPTION_PROBABILITY`    | Probability of simulated tool exception          |
| `RANDOM_TOOL_INVALID_JSON_PROBABILITY` | Probability of simulated invalid JSON response   |

---

## Built-in Tools

| Tool | Description |
|------|-------------|
| `BashTool` | Execute shell commands |
| `ReadTool` | Read file contents |
| `WriteTool` | Write content to files |
| `EditTool` | Edit files with exact string replacement |
| `GrepTool` | Search file contents with regex |
| `FindTool` | Find files by glob pattern |
| `LsTool` | List directory contents |
| `HttpTool` | Make HTTP requests |
| `WebFetchTool` | Fetch and render web pages |
| `WebSearchTool` | Search the web |
| `CalculatorTool` | Evaluate mathematical expressions |
| `TimeTool` | Get current date/time |
| `McpToolMethod` | Bridge to MCP server tools |

---

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.
