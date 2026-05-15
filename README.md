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
var agent = new StrandsAgent(model, registry, new ToolExecutor());

// 4. Optional: Events beobachten
agent.setEventListener(event -> System.out.println("Event: " + event));

// 5. Prompt ausführen
AgentResult result = agent.execute("Berechne 3 + 4");
System.out.println(result.finalAnswer());
```

Mit MockChatModel (kein API-Key):
```java
var agent = new StrandsAgent(new MockChatModel());
var result = agent.execute("Hallo Welt");
```

## Projektstruktur

```
strands-agents-java
 ├── strands-agents-core       Agent Loop, ToolRegistry, Event-System, Datenmodelle
 ├── strands-agents-sessions   ChatMemoryStores (InMemory, File)
 └── docs/                     Phasenplan, Architektur, Entscheidungen
```

Zukünftige Module (geplant):
- `strands-agents-mcp` – MCP Client für dynamische Tools (Step 9)
- `strands-agents-telemetry` – OpenTelemetry + Micrometer (Step 13)

## Phasenübersicht

| Phase | Thema | Status |
|---|---|---|
| **1** Foundation – Setup, Agent Loop, Tools, Events, A2A | ✅ **Komplett** |
| **2** Robustheit & Konversation – Conversation Manager, Session Persistierung | 🔲 Geplant |
| **3** Modell- & Tool-Diversifikation – Multi-Provider, MCP Client | 🔲 Geplant |
| **4** Event-Loop Optimierung – Token Recovery, Retries, Streaming | 🔲 Geplant |
| **5** Orchestrierung & Ökosystem – Enhanced A2A, LLM Routing, Telemetry | 🔲 Geplant |
| **6** Enterprise (optional) – Vault, DB, Kafka, K8s, Spring Boot | 📅 Optional |

Details in [`docs/`](docs/).
