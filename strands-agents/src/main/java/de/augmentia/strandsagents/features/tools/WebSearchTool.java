package de.augmentia.strandsagents.features.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.prompt.PromptRegistry;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSearchTool implements AgentTool<WebSearchTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final int MAX_RESULTS = 5;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String apiKey;
    private final String baseUrl = "https://api.tavily.com";

    public WebSearchTool() {
        var key = System.getProperty("vault.TAVILY_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv("TAVILY_API_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("TAVILY_API_KEY");
        }
        this.apiKey = key;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the web using Tavily Search API. Returns top " + MAX_RESULTS + " results.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public ObjectNode parameterSchema() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        var queryNode = props.putObject("query");
        queryNode.put("type", "string");
        queryNode.put("description", "The search query");
        schema.putArray("required").add("query");
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: web_search START query={}", params.query());
        var query = params.query();
        if (query == null || query.isBlank()) {
            return ToolResult.error("Query is required");
        }

        try {
            if (apiKey == null || apiKey.isBlank()) {
                return mockSearch(query);
            }
            return searchWithTavily(query);
        } catch (Exception e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private ToolResult searchWithTavily(String query) throws Exception {
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
            return ToolResult.error("Tavily API error: " + response.statusCode());
        }

        return parseResults(query, response.body());
    }

    private ToolResult parseResults(String query, String json) {
        var results = new StringBuilder();
        results.append("Search results for: ").append(query).append("\n\n");

        try {
            var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var arr = root.get("results");
            if (arr == null || !arr.isArray()) {
                return ToolResult.success("No results found");
            }
            int count = 0;
            for (var item : arr) {
                if (count >= MAX_RESULTS) {
                    break;
                }
                var title = item.has("title") ? item.get("title").asText() : null;
                var url = item.has("url") ? item.get("url").asText() : null;
                var content = item.has("content") ? item.get("content").asText() : null;
                results.append(++count).append(". ");
                if (title != null) {
                    results.append(title).append("\n");
                }
                if (url != null) {
                    results.append(url).append("\n");
                }
                if (content != null && content.length() > 100) {
                    results.append(content, 0, 100).append("...\n");
                }
                results.append("\n");
            }
            if (count == 0) {
                return ToolResult.success("No results found");
            }
        } catch (Exception e) {
            log.debug("Tool: web_search parse error: {}", e.getMessage());
            return ToolResult.error("Failed to parse results: " + e.getMessage());
        }

        return new ToolResult(List.of(new TextContent(results.toString())), null);
    }

    private ToolResult mockSearch(String query) {
        return ToolResult.success(PromptRegistry.get("web_search_tool.mock_result", query));
    }

    public record Params(String query) {}
}
