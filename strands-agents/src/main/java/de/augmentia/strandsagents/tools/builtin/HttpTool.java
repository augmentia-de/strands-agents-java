package de.augmentia.strandsagents.tools.builtin;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpTool {

    private final HttpClient httpClient;
    private final boolean blockPrivateIps;

    public HttpTool() {
        this(true);
    }

    public HttpTool(boolean blockPrivateIps) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.blockPrivateIps = blockPrivateIps;
    }

    @Tool("Performs a GET request to a specified URL and returns the response body.")
    public String get(@P("The full URL for the GET request") String url) {
        try {
            checkUrl(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Strands-Agent/1.0 (Java)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(response);
        } catch (Exception e) {
            return "HTTP GET Error: " + e.getMessage();
        }
    }

    @Tool("Performs a POST request to a specified URL with a JSON body.")
    public String post(@P("The full URL for the POST request") String url,
                       @P("The JSON payload to send in the request body") String jsonBody) {
        try {
            checkUrl(url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Strands-Agent/1.0 (Java)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(response);
        } catch (Exception e) {
            return "HTTP POST Error: " + e.getMessage();
        }
    }

    private void checkUrl(String url) {
        if (!blockPrivateIps) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are allowed");
        }
        var lower = url.toLowerCase();
        if (lower.contains("localhost") || lower.contains("127.0.0.1")
            || lower.contains("192.168.") || lower.contains("10.")
            || lower.contains("172.16.") || lower.contains("172.17.")
            || lower.contains("172.18.") || lower.contains("172.19.")
            || lower.contains("172.20.") || lower.contains("172.21.")
            || lower.contains("172.22.") || lower.contains("172.23.")
            || lower.contains("172.24.") || lower.contains("172.25.")
            || lower.contains("172.26.") || lower.contains("172.27.")
            || lower.contains("172.28.") || lower.contains("172.29.")
            || lower.contains("172.30.") || lower.contains("172.31.")
            || lower.contains("[::1]") || lower.contains("0.0.0.0")) {
            throw new IllegalArgumentException("Access to private IP addresses is not allowed");
        }
    }

    private String formatResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            return String.format("HTTP %d Error: %s", response.statusCode(), response.body());
        }
    }
}
