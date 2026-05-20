# Strands Agents SDK – Java 21 Portierung

Portierung des **Strands Agents SDK (TypeScript)** nach **Java 21** mit **LangChain4j**.

> **Leitprinzip:** Umfassende Funktionalität ist wichtiger als das jeweils neueste LLM oder Drittsystem-Integrationen.  
> Daher wird zuerst das vollständige SDK-Kern-Feature-Set abgebildet (Phasen 1–5), bevor optionale Enterprise-Erweiterungen folgen (Phase 6).

## Voraussetzungen

- Java 21 (JDK) mit `--enable-preview`
- Maven 3.9+
- Optional: OpenAI API-Key (`OPENAI_API_KEY`) für echte LLM-Aufrufe

## Quick Start

```bash
# 1. Projekt bauen && Tests ausführen
./dev.sh test

# 2. Mit realem LLM (API-Key in set_keys.sh oder env):
export OPENAI_API_KEY=sk-...
mvn test -pl strands-agents-core -Dtest=AgentMvpIT
```

## Wie man den Agenten nutzt (aktueller Stand)

```java
// 1. LLM erstellen (übernimmt OPENAI_API_KEY, LLM_CHAT_MODEL etc. aus env)
ChatModel model = ModelFactory.createOpenAiFromEnv();

// 2. Tools registrieren
var registry = new ToolRegistry();
registry.register(new CalculatorTool());

// 3. Agent erstellen
var agent = new Agent(model, registry, new ToolExecutor());

// 4. Optional: Events beobachten
agent.setEventListener(event -> System.out.println("Event: " + event));

// 5. Prompt ausführen
AgentResult result = agent.execute("Berechne 3 + 4");
System.out.println(result.finalAnswer());
```

Mit MockChatModel (kein API-Key):
```java
var agent = new Agent(new MockChatModel());
var result = agent.execute("Hallo Welt");
```

## Projektstruktur

```
strands-agents-java (Parent)
 ├── strands-agents-core        Agent Loop, ToolRegistry, Event-System, Datenmodelle,
 │                              Resilience (Retry/CircuitBreaker/TokenRecovery),
 │                              Streaming, Enhanced Multi-Agent (A2A Executor, LlmRouter)
 ├── strands-agents-mcp         MCP Client: StdIO/SSE Transport, JSON-RPC, Tool-Discovery
 ├── strands-agents-sessions    ChatMemoryStores + SessionManager (File, JDBC)
 ├── strands-agents-telemetry   OpenTelemetry-Tracing, Micrometer-Metrics, Hook-System
 ├── strands-agents-examples    Demo: MainMock (Mock) + Main (OpenAI) + ChatCLI
 └── docs/                      Phasenplan, Architektur, Entscheidungen
```

## Phasenübersicht

| Phase | Thema | Status |
|---|---|---|
| **1** Foundation – Setup, Agent Loop, Tools, Events, A2A | ✅ **Komplett** |
| **2** Robustheit & Konversation – Conversation Manager, Session Persistierung | ✅ **Komplett** |
| **3** Modell- & Tool-Diversifikation – Multi-Provider, MCP Client | ⏸️ **Teilweise** (MCP ✅, Multi-Provider 🔲) |
| **4** Event-Loop Optimierung – Token Recovery, Retries, Streaming | ✅ **Komplett** |
| **5** Orchestrierung & Ökosystem – Enhanced A2A, LLM Routing, Telemetry | ✅ **Komplett** |
| **6** Enterprise (optional) – Vault, DB, Kafka, K8s, Spring Boot | 📅 Optional |

## Vergleich: Steps 14–22 mit aktuellem Implementationstand

Die Enterprise-Steps (Phase 6) sind optional und größtenteils nicht implementiert.
Einige Module enthalten jedoch bereits **Grundlagen**, die in diese Richtung weisen:

| Step | Thema | Status | Überschneidung mit aktueller Implementierung |
|------|-------|--------|----------------------------------------------|
| 14 | **HashiCorp Vault** – Secrets Management | 📅 Nicht impl. | Keine. API-Keys kommen weiterhin aus Umgebungsvariablen (`LlmConfig`). |
| 15 | **PostgreSQL** – Session-DB | 📅 Nicht impl. | **Teilweise:** `JdbcSessionManager` (in `strands-agents-sessions`) bietet bereits JDBC-basierte Session-Persistenz mit DataSource, auto-creates `sessions`-Tabelle und `MERGE INTO`-Syntax (H2-kompatibel). Für PostgreSQL muss lediglich ein `PGSimpleDataSource` übergeben werden. Ein `PostgresChatMemoryStore` fehlt jedoch. |
| 16 | **Redis** – Caching & Pub/Sub | 📅 Nicht impl. | Keine. Keine Redis-Abhängigkeit im Projekt. |
| 17 | **Vektordb (pgvector/Qdrant)** – RAG | 📅 Nicht impl. | Keine. Keine Embedding-Pipeline oder Vector-Store vorhanden. |
| 18 | **Apache Kafka** – Event Streaming | 📅 Nicht impl. | Keine. Keine Kafka-Abhängigkeit im Projekt. |
| 19 | **Elasticsearch / OpenSearch** – Logging | 📅 Nicht impl. | Keine. Aber: `LoggingHook` in `strands-agents-telemetry` bietet strukturiertes SLF4J-Logging aller Events (kann via Logback nach ES geleitet werden). |
| 20 | **Docker & Kubernetes** – Deployment | 📅 Nicht impl. | Kein Dockerfile, keine Helm-Charts, keine K8s-Manifeste im Java-Projekt. |
| 21 | **gRPC** – Inter-Agent-Kommunikation | 📅 Nicht impl. | Keine. Keine Protobuf-Definitionen oder gRPC-Server/Client. |
| 22 | **Spring Boot / Quarkus** – DI-Framework | 📅 Nicht impl. | Keine Spring-Boot- oder Quarkus-Abhängigkeit. Agent wird über `main()` + Builder gestartet. |

Details in [`docs/`](docs/) und [`docs/options.md`](docs/options.md).

## License

This project is a Java port of the [Strands Agents SDK](https://github.com/strands-agents) and is licensed under the **Apache License 2.0**.

See the [LICENSE](LICENSE) and [NOTICE](NOTICE) files for details.
