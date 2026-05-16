package com.strands.agents.core.tools;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tool for managing memories in Elasticsearch using text-based search.
 * This is the Java equivalent of the Python elasticsearch_memory.py tool,
 * but using pure Elasticsearch text search (BM25) instead of Bedrock embeddings.
 */
public class ElasticsearchMemoryTool {

    private final ElasticsearchClient client;
    private final String indexName;
    private final String namespace;

    public ElasticsearchMemoryTool(String serverUrl, String apiKey, String indexName, String namespace) {
        this.indexName = indexName != null ? indexName : "strands_memory";
        this.namespace = namespace != null ? namespace : "default";

        RestClient restClient = RestClient.builder(HttpHost.create(serverUrl))
            .setDefaultHeaders(new org.apache.http.Header[]{
                new org.apache.http.message.BasicHeader("Authorization", "ApiKey " + apiKey)
            })
            .build();

        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.client = new ElasticsearchClient(transport);
        ensureIndexExists();
    }

    private void ensureIndexExists() {
        try {
            boolean exists = client.indices().exists(e -> e.index(indexName)).value();
            if (!exists) {
                client.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                        .properties("memory_id", p -> p.keyword(k -> k))
                        .properties("content", p -> p.text(t -> t))
                        .properties("namespace", p -> p.keyword(k -> k))
                        .properties("timestamp", p -> p.date(d -> d))
                        .properties("metadata", p -> p.object(o -> o.enabled(true)))
                    )
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to ensure Elasticsearch index exists", e);
        }
    }

    @Tool("Stores a new memory in the long-term storage")
    public String record(@P("The text content to remember") String content, 
                        @P("Optional category or type for this memory") String category) {
        String memoryId = "mem_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("memory_id", memoryId);
        doc.put("content", content);
        doc.put("namespace", namespace);
        doc.put("timestamp", Instant.now().toString());
        if (category != null) {
            doc.put("metadata", Map.of("category", category));
        }

        try {
            var response = client.index(i -> i
                .index(indexName)
                .id(memoryId)
                .document(doc)
            );
            return "✅ Memory recorded successfully with ID: " + memoryId;
        } catch (IOException e) {
            return "❌ Error recording memory: " + e.getMessage();
        }
    }

    @Tool("Searches for relevant memories based on a text query")
    public String retrieve(@P("The search query or topic to find") String query) {
        try {
            SearchResponse<Map> response = client.search(s -> s
                .index(indexName)
                .query(q -> q
                    .bool(b -> b
                        .must(m -> m.match(t -> t.field("content").query(query)))
                        .filter(f -> f.term(t -> t.field("namespace").value(namespace)))
                    )
                )
                .size(5),
                Map.class
            );

            List<Map> hits = response.hits().hits().stream()
                .map(hit -> hit.source())
                .collect(Collectors.toList());

            if (hits.isEmpty()) {
                return "No relevant memories found for: " + query;
            }

            StringBuilder sb = new StringBuilder("Found the following relevant memories:\n");
            for (Map hit : hits) {
                sb.append("- ").append(hit.get("content")).append(" (ID: ").append(hit.get("memory_id")).append(")\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "❌ Error retrieving memories: " + e.getMessage();
        }
    }

    @Tool("Deletes a specific memory from storage")
    public String delete(@P("The unique ID of the memory to remove") String memoryId) {
        try {
            var response = client.delete(d -> d
                .index(indexName)
                .id(memoryId)
            );
            
            if (response.result() == Result.Deleted) {
                return "✅ Memory " + memoryId + " deleted successfully.";
            } else {
                return "⚠️ Memory not found or already deleted.";
            }
        } catch (IOException e) {
            return "❌ Error deleting memory: " + e.getMessage();
        }
    }

    @Tool("Lists the most recent memories in the current namespace")
    public String list() {
        try {
            SearchResponse<Map> response = client.search(s -> s
                .index(indexName)
                .query(q -> q.term(t -> t.field("namespace").value(namespace)))
                .sort(sort -> sort.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .size(10),
                Map.class
            );

            List<Map> hits = response.hits().hits().stream()
                .map(hit -> hit.source())
                .collect(Collectors.toList());

            if (hits.isEmpty()) {
                return "Your memory is currently empty.";
            }

            StringBuilder sb = new StringBuilder("Your most recent memories:\n");
            for (Map hit : hits) {
                sb.append("• ").append(hit.get("content")).append(" [").append(hit.get("timestamp")).append("]\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return "❌ Error listing memories: " + e.getMessage();
        }
    }
}
