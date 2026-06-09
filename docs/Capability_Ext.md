# Capability_ext.md: Scalable Tool Discovery & Hybrid RAG

## Architecture Overview

When the number of available tools (skills & MCP servers) exceeds 15, passing all of them directly to the LLM causes context overload ("Lost in the Middle") and high costs. Therefore we implement a **Pluggable Discovery Layer** that provides only the most relevant tools at runtime.

## 1. The Strategy: Hybrid Retrieval

We combine semantic search (for intent recognition) with keyword search (for exact matches on tool names or IDs).

- **Vector Search:** Finds tools based on the meaning of the user query.
- **BM25 (Sparse):** Ensures the correct tool is found when exact names or technical terms are used.
- **RRF (Reciprocal Rank Fusion):** Combines results from both strategies into a consistent ranking.

## 2. The "Pluggable" Discovery Service

This service acts as a proxy in front of the agent. It encapsulates the vector database and the tool selection logic.

### Implementation Concept

```java
public interface ToolDiscoveryProvider {
    List<Object> getRelevantTools(String task);
}
```

### Best Practice Workflow

- **Ingestion:** At startup the service registers all tools. For each tool we generate "pseudo-queries" (via LLM prompting: "Which questions does this tool answer?") and store these as vectors.
- **Runtime Caching:**
  - Use `io.quarkus.cache.Cache` on the `getRelevantTools` method.
  - Use a Semantic Cache: if the embedding similarity to the last request exceeds 0.95, return the same tool list from cache.
- **Hybrid Search:**
  - Database: Qdrant or pgvector (Postgres) are recommended for hybrid queries (dense + sparse vectors).

## 3. Implementation Example (Quarkus/LangChain4j)

```java
@ApplicationScoped
public class HybridToolDiscoveryService implements ToolDiscoveryProvider {

    @Inject EmbeddingModel embeddingModel;
    @Inject EmbeddingStore<TextSegment> embeddingStore;

    @CacheResult(cacheName = "tool-discovery-cache")
    public List<Object> getRelevantTools(String task) {
        // 1. Generate embedding
        Embedding queryEmbedding = embeddingModel.embed(task).content();

        // 2. Perform hybrid search (pseudocode)
        // Here: Qdrant/pgvector integration for hybrid search
        var matches = embeddingStore.search(EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(5)
            .minScore(0.7)
            .build());

        // 3. Map back to tool instances
        return matches.matches().stream()
            .map(m -> lookupToolInstance(m.embedded().metadata().get("toolName")))
            .collect(Collectors.toList());
    }
}
```

## 4. Design Principles for "Pluggable" Tools

- **Loose Coupling:** The main model does not know the tools; it receives them dynamically at runtime via a `@ToolProvider` mechanism.
- **Metadata Enrichment:** Each tool must have a `description` that not only describes the function but also contains "trigger words" for the LLM.
- **Fail-Safe:** If the search finds no tool with a high score, the agent should give a generic answer or ask for clarification rather than selecting wrong tools.

## 5. Performance Tuning (Checklist)

- **Batch Ingestion:** Load tool definitions asynchronously at startup; do not block server startup.
- **TTL (Time-to-Live):** Set the cache to at least 30-60 minutes, as tool definitions change infrequently.
- **Invalidation:** Implement an EventBus observer that clears the cache as soon as a new MCP server is registered via the Admin API.

## Recommended Technologies

- **Vector DB:** Qdrant (native Hybrid Search).
- **Embedding Model:** text-embedding-3-small (cost-efficient and fast).
- **Framework:** quarkus-langchain4j.
