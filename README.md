# Strands Agents SDK – Java 21

A Java 21 port of the [Strands Agents SDK (TypeScript)](https://github.com/strands-agents), built on **LangChain4j** with optional **Quarkus** or **Spring Boot** REST frontends.

> **Status:** Experimental. API is unstable and will change without notice.

---

## Features

| Area | Capabilities |
|------|-------------|
| **Agent Loop** | Manual tool-calling loop (IDLE → THINKING → OBSERVING → ACTING → FINISHED), multi-turn conversation, streaming, planning (CoT), swarm, A2A sub-agent delegation, LLM-based routing |
| **Tools** | 17+ built-in tools: bash, read/write/edit files, web fetch/search, HTTP, calculator, time, Docker, MCP bridge, capability search, tool reflection |
| **Multi-Tier LLM** | Simple / Advanced / Routing modes — switchable per session. Routing tier auto-classifies user goals via simple model and escalates to advanced model for complex reasoning |
| **Resilience** | Retry (exponential backoff), Circuit Breaker (3-state), TokenRecovery (auto-truncate on context-length exceeded) |
| **Conversation & Sessions** | Sliding window or LLM-summarizing conversation pruning; file or JDBC session persistence |
| **Plugins** | GuardrailPlugin (input/output content safety), HITLPlugin (human-in-the-loop approval), AgentSkillsPlugin (dynamic skill injection) |
| **Hooks** | 6 lifecycle interception points (before/after agent, model call, tool call) with Continue/Cancel/Modify/Retry results |
| **Structured Output** | JSON schema via OpenAI `response_format` or force-prompt fallback |
| **Skills & Capabilities** | Markdown files with YAML frontmatter; CapabilityRegistry aggregates skills + MCP tools; runtime search via CapabilitySearchTool |
| **MCP** | Model Context Protocol client with stdio/SSE transport, multi-server namespace isolation (`serverName_toolName`) |
| **Secrets / Vault** | HashiCorp Vault, AES-256/GCM encrypted key store (PBKDF2), composite fallback chain |
| **A2A** | Agent-to-Agent protocol with sub-agent delegation, recursion guard (max 5 levels), virtual threads |
| **Telemetry** | AgentEvent lifecycle events, OpenTelemetry tracing, Micrometer metrics, rolling LLM call logger |
| **Chaos Engineering** | Random injection of tool timeouts, exceptions, and invalid JSON (`RANDOM_TOOL_ERRORS_ENABLED`) |
| **REST API (Quarkus)** | Chat (sync + SSE streaming), agent init/release, tool/skill/session management, MCP discovery, key vault admin, Swagger UI |
| **Spring Boot** | Alternative REST frontend |

### What is not implemented
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

### Core SDK (no API key needed)

```bash
./scripts/dev.sh build
./scripts/dev.sh test
./scripts/dev.sh run-mock
./scripts/dev.sh chat --mock
```

### Quarkus REST API

```bash
./scripts/start-quarkus.sh dev    # http://localhost:8082, Swagger at /q/swagger-ui
```

### Docker

```bash
./scripts/build.sh                # JVM image → strands-agent:latest
./scripts/deploy.sh --jvm         # docker compose up on port 8082
```

---

## Startup Scripts

| Script | Description |
|--------|-------------|
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

See [API.md](API.md) for the complete configuration reference. Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | — | API key |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Base URL |
| `OPENAI_MODEL` | `gpt-4o` | Model name |
| `LLM_TEMPERATURE` | `0.7` | Temperature |
| `LLM_DEFAULT_TIER` | `simple` | Model tier: `simple`, `advanced`, `routing` |
| `STRANDS_SKILLS_DIR` | `skills` | Skills directory |
| `STRANDS_SESSION_DIR` | `.sessions` | Session persistence directory |
| `STRANDS_AGENT_BASH_ALLOW` | `false` | Allow `BashTool` |
| `STRANDS_MCP_CONFIG` | `config/MCP_SERVER_CONFIG.json` | MCP server config path |
| `TAVILY_API_KEY` | — | Web search API key (Tavily) |

Multi-tier LLM: `SIMPLE_*` / `ADVANCED_*` env vars per tier (provider, model, api key, base url).

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
