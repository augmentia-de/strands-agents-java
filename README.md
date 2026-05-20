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
- Optional: OpenAI API key (`OPENAI_API_KEY`) for real LLM calls

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
// 1. Create an LLM (reads OPENAI_API_KEY, LLM_CHAT_MODEL from env)
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

| Environment variable | Property | Default | Description |
|---------------------|----------|---------|-------------|
| `OPENAI_API_KEY` | — | — | OpenAI API key |
| `LLM_CHAT_MODEL` | — | `gpt-4o` | Model name |
| `LLM_BASE_URL` | — | `https://api.openai.com` | API base URL |
| `STRANDS_SKILLS_DIR` | `strands.agent.skills.dir` | `skills` | Skills directory |
| `STRANDS_SESSION_DIR` | `strands.agent.session.dir` | `.sessions` | Session storage directory |
| `STRANDS_LLM_LOG_ENABLED` | `strands.agent.llm-log.enabled` | `true` | Enable LLM call logging |
| `STRANDS_LLM_LOG_PATH` | `strands.agent.llm-log.path` | `logs/llm-calls.log` | LLM log file path |
| `STRANDS_AGENT_TOOLS` | `strands.agent.tools` | — | Comma-separated extra tool class names |
| `STRANDS_MCP_URL` | `strands.agent.mcp.url` | `http://localhost:8888/mcp` | Default MCP server URL |

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
| `ElasticsearchMemoryTool` | Query Elasticsearch for conversational memory |

---

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.
