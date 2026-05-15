# Strands Agents SDK – Java 21 Portierung

Portierung des Python **Strands Agents SDK** nach **Java 21** mit **LangChain4j**.

## Voraussetzungen

- Java 21 (JDK)
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

## Phasenübersicht

| Phase | Thema | Status |
|---|---|---|
| 1 | Datenmodell, Maven-Setup | ✅ |
| 2 | Core Agent Loop | ✅ |
| 3 | Tool-Infrastruktur, Session-Stores | ✅ |
| 4 | Event-System, Observability | ✅ |
| 5 | Multi-Agent, MCP, A2A | 📅 |

Details in [`docs/`](docs/).
