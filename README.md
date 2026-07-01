# Strands Agents SDK – Java 21

A Java 21 port of the [Strands Agents SDK (TypeScript)](https://github.com/strands-agents), built on **LangChain4j** with optional **Quarkus** or **Spring Boot** REST frontends.

> **Status:** Experimental. API is unstable and will change without notice.

---

## Why Strands?

| | Feature | What makes it different |
|--|---------|------------------------|
| 🔌 | **Pipeline Hooks** | Not a simple "LLM call in a loop" — a **6-stage pipeline** with interception points. Hooks can rewrite prompts, tools, and responses *before execution*, cancel execution, or force retries — all decoupled in plugins, not in the agent loop itself. |
| 🛡️ | **Guardrails + Human-in-the-Loop** | Guardrails have three escalation levels: `THROW` (hard abort), `FALLBACK` (sanitized response), **`ESCALATE`** — the agent thread pauses via `ReentrantLock`, creates a `Checkpoint` object, and waits for human approval through the REST API. Agent state is an explicit state machine model. |
| ⚡ | **Resilience Stack** | **Three orthogonal patterns composed**: Retry (exponential backoff, skips auth errors) + Circuit Breaker (3-state, sliding-window failure rate) + **TokenRecovery** (detects context overflow across providers, prunes history intelligently). Not a simple retry wrapper — production-grade hardening. |
| 💰 | **Tiered Model Routing** | `RoutingAgent` holds two models (cheap/expensive). An LLM-powered router classifies *per request* whether it's a simple or complex query. Simple queries → cheap model, reasoning → expensive model. Cost efficiency without manual routing logic. |
| 🔍 | **Capability Registry + Sub-Agent** | The framework introspects **itself**: `CapabilityRegistry` aggregates skills, MCP tools, and built-in tools. A dedicated *sub-agent* (`CapabilitySearchAgent`) matches user tasks against all available capabilities via LLM — including dynamic **Tool Enrichment** recommendations. Metacognition. |

---

## Features

| Area | Capabilities |
|------|-------------|
| **Agent Loop** | Pipeline-based tool-calling loop (IDLE → THINKING → OBSERVING → ACTING → FINISHED) with 6 hook points; multi-turn, streaming, CoT planning, swarm, A2A sub-agent delegation, LLM-based routing |
| **Tools** | 17+ built-in tools: Bash, Read/Write/Edit, Web Fetch/Search, HTTP, Calculator, Time, Docker, MCP bridge, CapabilitySearch, Tool Reflection |
| **Multi-Tier LLM** | Simple / Advanced / Routing — switchable per session. Routing tier classifies user goals via cheap model, escalates complex queries to a more capable model |
| **Resilience** | Retry (exponential backoff), Circuit Breaker (3-state), TokenRecovery (auto-truncate on context overflow), timeout |
| **Conversation & Sessions** | Sliding window or LLM-summarizing conversation pruning; file or JDBC session persistence |
| **Plugins** | GuardrailPlugin (input/output), HITLPlugin (human-in-the-loop), AgentSkillsPlugin (dynamic skill injection) |
| **Hooks** | 6 lifecycle interceptions (before/after agent, model call, tool call) with Continue/Cancel/Modify/Retry |
| **Structured Output** | JSON schema via `response_format` or force-prompt fallback |
| **Skills & Capabilities** | Markdown + YAML frontmatter; CapabilityRegistry aggregates skills + MCP tools; runtime search via CapabilitySearchTool with sub-agent |
| **MCP** | Model Context Protocol client with stdio/SSE transport, multi-server namespace isolation |
| **Secrets / Vault** | HashiCorp Vault, AES-256/GCM key store (PBKDF2), composite fallback chain |
| **A2A** | Agent-to-Agent protocol with sub-agent delegation, recursion guard (max 5 levels), virtual threads |
| **Telemetry** | AgentEvent lifecycle events, OpenTelemetry tracing, Micrometer metrics, rolling LLM call logger |
| **Model Factory** | Multi-provider: OpenAI, Ollama, OpenAiCompatible (LM Studio, vLLM, Together AI); sync-to-streaming bridge; plugin registration |
| **Chaos Engineering** | Random injection of tool timeouts, exceptions, and invalid JSON |
| **REST API (Quarkus)** | Chat (sync + SSE streaming), agent init/release, tool/skill/session management, MCP discovery, key vault admin, Swagger UI |
| **Spring Boot** | Alternative REST frontend |

### Not implemented
- Kafka event streaming
- Vector database / RAG pipeline
- gRPC inter-agent communication
- Redis caching / pub-sub

---

## Prerequisites

| Dependency | Version | Required for |
|------------|---------|-------------|
| Java JDK | 21+ (with `--enable-preview`) | Local dev, Quarkus/Spring modes |
| Maven | 3.9+ | Building from source |
| Docker | 24+ | Container builds, deployments, Quarkus dev mode |
| Docker Compose | v2 | Local deployment |
| OpenAI-compatible API key | — | LLM access (stored in PBE vault) |

---

## Quick Start

### Core SDK (mock mode, no API key required)

```bash
./scripts/dev.sh build
./scripts/dev.sh test
./scripts/dev.sh run-mock
./scripts/dev.sh chat --mock
```

### Quarkus REST API (with real LLM)

```bash
source set_keys.sh                          # sets OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL
./scripts/start-quarkus.sh dev              # http://localhost:8082, Swagger at /q/swagger-ui
```

### Docker

```bash
source set_keys.sh                          # env vars for the container
./scripts/build.sh                          # JVM image → strands-agent:latest
./scripts/deploy.sh --jvm                   # docker compose up on port 8082
```

---

## Startup Scripts

| Script | Description |
|--------|-------------|
| `set_keys.sh` | Sets API key, base URL, and model as env vars (e.g. `source set_keys.sh`) |
| `scripts/dev.sh` | Build, test, run demos (`build`, `test`, `run`, `run-mock`, `chat`, `chat --mock`) |
| `scripts/start-quarkus.sh` | Quarkus dev/prod (`dev`, `prod`, `build-only`) port 8082 |
| `scripts/start-spring.sh` | Spring Boot dev/prod (`dev`, `dev-ui`, `prod`, `build-only`) port 8081 |
| `scripts/build.sh` | Docker JVM/native/lambda images |
| `scripts/deploy.sh` | Docker Compose or GCP deployment |
| `scripts/start-main.sh` | Run `SelfImprovementDemo` |

---

## Project Structure

```
strands-agents-java (parent)
├── strands-agents                Core library: agent loop, tools, resilience,
│                                  conversation, sessions, plugins, A2A, planning,
│                                  routing, swarm, skills, MCP, telemetry, vault
├── strands-agents-quarkus        Quarkus REST API (chat, SSE, admin, Swagger UI)
├── strands-agents-spring         Spring Boot REST API (alternative frontend)
├── strands-agents-examples       Demo applications
├── strands-a2a                   A2A protocol support
├── strands-cli                   CLI tools
├── strands-secret-provider-*     Secret provider implementations (AWS, GCP)
├── docker/                       Docker Compose + Dockerfiles
├── deploy/                       Kubernetes manifests
└── docs/                         Architecture, roadmap, capability extensions
```

---

## Configuration

See [config.md](config.md) for the complete configuration reference. Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | — | API key (OpenAI, OpenRouter, OpenAiCompatible) |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Base URL (e.g. `https://openrouter.ai/api/v1`) |
| `OPENAI_MODEL` | `gpt-4o` | Model name (e.g. `deepseek/deepseek-v4-flash`) |
| `LLM_TEMPERATURE` | `0.7` | Temperature |
| `LLM_DEFAULT_TIER` | `simple` | Model tier: `simple`, `advanced`, `routing` |
| `STRANDS_SKILLS_DIR` | `skills` | Skills directory |
| `STRANDS_SESSION_DIR` | `.sessions` | Session persistence directory |
| `STRANDS_AGENT_BASH_ALLOW` | `false` | Allow `BashTool` |
| `STRANDS_MCP_CONFIG` | `config/MCP_SERVER_CONFIG.json` | MCP server config path |
| `TAVILY_API_KEY` | — | Web search API key (Tavily) |

**Multi-Tier LLM** — configurable per tier via env vars:
`SIMPLE_PROVIDER`, `SIMPLE_MODEL`, `SIMPLE_API_KEY`, `SIMPLE_BASE_URL`<br>
`ADVANCED_PROVIDER`, `ADVANCED_MODEL`, `ADVANCED_API_KEY`, `ADVANCED_BASE_URL`

Fallback chain: `SIMPLE_MODEL` → `OPENAI_MODEL` → `gpt-4o-mini`. See [config.md](config.md) for details.

---

## Built-in Tools

| Tool | Description |
|------|-------------|
| `BashTool` | Execute shell commands (workspace-sandboxed) |
| `ReadTool`, `WriteTool`, `EditTool` | File I/O with path-traversal protection |
| `GrepTool`, `FindTool`, `LsTool` | File search |
| `HttpTool` | HTTP requests (private IP blocked by default) |
| `WebFetchTool`, `WebSearchTool` | Web access (Tavily) |
| `CalculatorTool` | Arithmetic operations |
| `TimeTool` | Current date/time |
| `DockerRunTool` | Container-sandboxed code execution |
| `HumanInTheLoopTool` | Explicit human approval prompt |
| `McpToolMethod` | Bridge to MCP server tools |
| `CapabilitySearchTool` | Runtime skill discovery |
| `ListToolsTool` | Tool reflection |

---

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
