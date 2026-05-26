package de.augmentia.strandsagents.mcp.server.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.http.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpWebSearchTool {
    private static final Logger log = LoggerFactory.getLogger(McpWebSearchTool.class);
    private static final int MAX_RESULTS = 5;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiKey;
    private final String baseUrl = "https://api.tavily.com";

    public McpWebSearchTool() {
        this.apiKey = System.getenv("TAVILY_API_KEY");
    }

    @Tool("Search the web using Tavily Search API. Returns top " + MAX_RESULTS + " results.")
    public String webSearch(@P("The search query") String query) {
        log.debug("web_search START query={}", query);
        if (query == null || query.isBlank()) return "Query is required";

        try {
            if (apiKey == null || apiKey.isBlank()) return mockSearch(query);
            return searchWithTavily(query);
        } catch (Exception e) {
            return "Search failed: " + e.getMessage();
        }
    }

    private String searchWithTavily(String query) throws Exception {
        var requestBody = String.format(
            "{\"api_key\":\"%s\",\"query\":\"%s\",\"max_results\":%d}",
            apiKey, query.replace("\"", "\\\""), MAX_RESULTS);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/search"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "Tavily API error: " + response.statusCode();
        }

        return parseResults(query, response.body());
    }

    private String parseResults(String query, String json) {
        var sb = new StringBuilder();
        sb.append("Search results for: ").append(query).append("\n\n");

        try {
            var root = new ObjectMapper().readTree(json);
            var arr = root.get("results");
            if (arr == null || !arr.isArray()) return "No results found";

            int count = 0;
            for (var item : arr) {
                if (count >= MAX_RESULTS) break;
                var title = item.has("title") ? item.get("title").asText() : null;
                var url = item.has("url") ? item.get("url").asText() : null;
                var content = item.has("content") ? item.get("content").asText() : null;
                sb.append(++count).append(". ");
                if (title != null) sb.append(title).append("\n");
                if (url != null) sb.append(url).append("\n");
                if (content != null && content.length() > 100) {
                    sb.append(content, 0, 100).append("...\n");
                }
                sb.append("\n");
            }
            if (count == 0) return "No results found";
        } catch (Exception e) {
            return "Failed to parse results: " + e.getMessage();
        }
        return sb.toString();
    }

    private String mockSearch(String query) {
        return """
            Search results for: %s

            1. Example Result 1
            https://example.com/result1
            This is a mock result for demonstration.

            2. Example Result 2
            https://example.com/result2
            Configure TAVILY_API_KEY for real results.

            Note: Set environment variable TAVILY_API_KEY for real search results.
            Get free API key at https://tavily.com
            """.formatted(query);
    }
}
