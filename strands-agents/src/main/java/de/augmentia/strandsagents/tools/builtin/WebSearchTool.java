package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.JsonContent;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.prompt.PromptRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSearchTool implements AgentTool<WebSearchTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
        return BaseToolNames.WEB_SEARCH;
    }

    @Override
    public String description() {
        return "Search the web using Tavily Search API. Returns JSON: {\"query\":\"...\",\"totalResults\":N,\"results\":[{\"title\":\"...\",\"url\":\"...\",\"content\":\"...\"}]}.";
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
        try {
            var root = MAPPER.readTree(json);
            var arr = root.get("results");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                return ToolResult.json(buildEmptyJson(query));
            }

            var resultRoot = MAPPER.createObjectNode();
            resultRoot.put("query", query);
            var resultsArr = resultRoot.putArray("results");
            int count = 0;
            for (var item : arr) {
                if (count >= MAX_RESULTS) {
                    break;
                }
                var obj = resultsArr.addObject();
                obj.put("title", item.has("title") ? item.get("title").asText() : "");
                obj.put("url", item.has("url") ? item.get("url").asText() : "");
                obj.put("content", item.has("content") ? item.get("content").asText() : "");
                count++;
            }
            resultRoot.put("totalResults", resultsArr.size());

            return ToolResult.json(resultRoot);
        } catch (Exception e) {
            log.debug("Tool: web_search parse error: {}", e.getMessage());
            return ToolResult.error("Failed to parse results: " + e.getMessage());
        }
    }

    private ObjectNode buildEmptyJson(String query) {
        return MAPPER.createObjectNode()
            .put("query", query)
            .put("totalResults", 0);
    }

    private ToolResult mockSearch(String query) {
        var mockText = PromptRegistry.get("web_search_tool.mock_result", query);
        try {
            var root = MAPPER.createObjectNode();
            root.put("query", query);
            var arr = root.putArray("results");
            arr.addObject()
                .put("title", "Mock Result")
                .put("url", "https://example.com")
                .put("content", mockText);
            root.put("totalResults", 1);
            return ToolResult.json(root);
        } catch (Exception e) {
            var root = MAPPER.createObjectNode();
            root.put("query", query);
            var arr = root.putArray("results");
            arr.addObject()
                .put("title", "Mock Result")
                .put("url", "https://example.com")
                .put("content", mockText);
            root.put("totalResults", 1);
            return ToolResult.json(root);
        }
    }

    public record Params(String query) {}
}
