package com.strands.agents.core.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Universal HTTP Tool for performing REST API calls (GET, POST, etc.).
 * This is a "Power-Tool" for the agent to interact with any external API.
 */
public class HttpTool {

    private final HttpClient httpClient;

    public HttpTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool("Performs a GET request to a specified URL and returns the response body.")
    public String get(@P("The full URL for the GET request") String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Strands-Agent/1.0 (Java)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(response);
        } catch (Exception e) {
            return "❌ HTTP GET Error: " + e.getMessage();
        }
    }

    @Tool("Performs a POST request to a specified URL with a JSON body.")
    public String post(@P("The full URL for the POST request") String url, 
                       @P("The JSON payload to send in the request body") String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Strands-Agent/1.0 (Java)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(response);
        } catch (Exception e) {
            return "❌ HTTP POST Error: " + e.getMessage();
        }
    }

    private String formatResponse(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            return String.format("⚠️ HTTP %d Error: %s", response.statusCode(), response.body());
        }
    }
}
