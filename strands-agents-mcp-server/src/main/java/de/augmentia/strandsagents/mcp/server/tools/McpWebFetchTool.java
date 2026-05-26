package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.http.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpWebFetchTool {
    private static final Logger log = LoggerFactory.getLogger(McpWebFetchTool.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$");
    private static final int MAX_CHARS = 30_000;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Tool("Fetch a web page and extract its text content.")
    public String webFetch(@P("The URL of the web page to fetch") String url) {
        log.debug("web_fetch url={}", url);
        if (url == null || url.isBlank()) return "URL is required";
        if (!URL_PATTERN.matcher(url).matches()) return "Invalid URL format";
        if (!isUrlAllowed(url)) return "Access to this URL is not allowed";

        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "StrandsBot/1.0")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "HTTP " + response.statusCode() + ": " + getHttpErrorMessage(response.statusCode());
            }

            var body = response.body();
            var title = extractTitle(body);
            var text = extractText(body);

            var result = new StringBuilder();
            if (title != null) result.append("Title: ").append(title).append("\n\n");
            result.append(text);

            if (result.length() > MAX_CHARS) {
                result.setLength(MAX_CHARS);
                result.append("\n\n[Output truncated]");
            }
            return result.toString();
        } catch (Exception e) {
            return "Failed to fetch: " + e.getMessage();
        }
    }

    private String extractTitle(String html) {
        var start = html.indexOf("<title>");
        if (start == -1) return null;
        var end = html.indexOf("</title>", start);
        return end == -1 ? null : html.substring(start + 7, end).trim();
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
}
