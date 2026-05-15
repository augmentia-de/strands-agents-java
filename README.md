# Strands Agents SDK – Java 21 Portierung

Portierung des Python **Strands Agents SDK** nach **Java 21** mit **LangChain4j**.

## Voraussetzungen

- Java 21 (JDK)
- Maven 3.9+
- Optional: OpenAI API-Key (`OPENAI_API_KEY`) für echte LLM-Aufrufe

## Quick Start

```bash
# 1. Projekt bauen
./dev.sh build

# 2. Unit-Tests ausführen (ohne API-Key)
./dev.sh test

# 3. Beispiel mit Mock starten (kein API-Key nötig)
./dev.sh run-mock

# 4. Mit OpenAI starten
export OPENAI_API_KEY=sk-...
./dev.sh run
```

## Projektstruktur

```
strands-agents-java
 ├── strands-agents-core       Core Loop, Datenmodelle, Agent
 ├── strands-agents-tools      Tool-Registry, -Loader (Phase 3)
 ├── strands-agents-sessions   ChatMemoryStores (Phase 3+)
 └── strands-agents-examples   Beispiele & Integrationstests
```

## Nächste Schritte

| Phase | Thema | Status |
|---|---|---|
| 1 | Datenmodell, Specs, Maven | ✅ |
| 2 | Core Agent Loop, ChatMemory | ✅ |
| 3 | Tool-Infrastruktur, Session-Stores | 🔜 |
| 4 | Hooks, Streaming, Observability | 📅 |
| 5 | Multi-Agent, MCP, A2A | 📅 |

Details in [`docs/`](docs/).
