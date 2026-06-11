package de.augmentia.strandsagents.features.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebFetchTool implements AgentTool<WebFetchTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(WebFetchTool.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
    private static final int MAX_CHARS = 30_000;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String name() {
        return "web_fetch";
    }

    @Override
    public String description() {
        return "Fetch a web page and extract its text content.";
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
        var urlNode = props.putObject("url");
        urlNode.put("type", "string");
        urlNode.put("description", "The URL of the web page to fetch");
        schema.putArray("required").add("url");
        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: web_fetch url={}", params.url());
        var url = params.url();
        if (url == null || url.isBlank()) {
            return ToolResult.error("URL is required");
        }
        if (!URL_PATTERN.matcher(url).matches()) {
            return ToolResult.error("Invalid URL format");
        }
        if (!isUrlAllowed(url)) {
            return ToolResult.error("Access to this URL is not allowed");
        }

        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "StrandsBot/1.0")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return ToolResult.error("HTTP " + response.statusCode() + ": " + getHttpErrorMessage(response.statusCode()));
            }

            var body = response.body();
            var title = extractTitle(body);
            var text = extractText(body);

            var result = new StringBuilder();
            if (title != null) {
                result.append("Title: ").append(title).append("\n\n");
            }
            result.append(text);

            if (result.length() > MAX_CHARS) {
                result.setLength(MAX_CHARS);
                result.append("\n\n[Output truncated]");
            }

            return new ToolResult(List.of(new TextContent(result.toString())), null);
        } catch (Exception e) {
            return ToolResult.error("Failed to fetch: " + e.getMessage());
        }
    }

    private String extractTitle(String html) {
        var start = html.indexOf("<title>");
        if (start == -1) {
            return null;
        }
        var end = html.indexOf("</title>", start);
        if (end == -1) {
            return null;
        }
        return html.substring(start + 7, end).trim();
    }

    private String extractText(String html) {
        var text = html
            .replaceAll("(?s)<script[^>]*>.*?</script>", "")
            .replaceAll("(?s)<style[^>]*>.*?</style>", "")
            .replaceAll("<[^>]+>", "")
            .replaceAll("\\s+", " ")
            .trim();
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }

    private boolean isUrlAllowed(String url) {
        if (url.contains("localhost") || url.contains("127.0.0.1")
            || url.contains("192.168.") || url.contains("10.")
            || url.contains("172.16.") || url.contains("172.17.")
            || url.contains("172.18.") || url.contains("172.19.")) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private String getHttpErrorMessage(int statusCode) {
        return switch (statusCode) {
            case 404 -> "Page not found";
            case 403 -> "Access forbidden";
            case 401 -> "Authentication required";
            case 500, 502, 503, 504 -> "Server error";
            default -> "Request failed";
        };
    }

    public record Params(String url) {}
}
