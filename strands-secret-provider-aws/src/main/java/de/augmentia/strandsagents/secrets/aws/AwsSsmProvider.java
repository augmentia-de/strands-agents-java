package de.augmentia.strandsagents.secrets.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.config.secrets.SecretProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwsSsmProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsSsmProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_ENDPOINT = "http://localhost:2773";

    private final HttpClient client;
    private final String endpoint;
    private final String ssmPath;

    public AwsSsmProvider(String ssmPath) {
        this(DEFAULT_ENDPOINT, ssmPath);
    }

    public AwsSsmProvider(String endpoint, String ssmPath) {
        this.endpoint = endpoint != null ? endpoint : DEFAULT_ENDPOINT;
        this.ssmPath = ssmPath;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        log.info("AwsSsmProvider: endpoint={} ssmPath={}", this.endpoint, this.ssmPath);
    }

    @Override
    public String getSecret(String path, String key) {
        var targetPath = ssmPath;
        if (targetPath == null || targetPath.isBlank()) {
            targetPath = path + "/" + key;
        }
        try {
            var encoded = URLEncoder.encode(targetPath, StandardCharsets.UTF_8);
            var url = endpoint + "/ssm/parameters/" + encoded + "?withDecryption=true";
            log.info("AwsSsmProvider: fetching {} from SSM Parameter Store", targetPath);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("AwsSsmProvider: SSM returned {}: {}", response.statusCode(), response.body());
                return null;
            }

            var root = MAPPER.readValue(response.body(), Map.class);
            var param = (Map<String, Object>) root.get("Parameter");
            if (param == null) return null;
            var value = (String) param.get("Value");
            if (value != null && !value.isBlank()) {
                log.info("AwsSsmProvider: found secret at {}", targetPath);
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("AwsSsmProvider: failed to read {}: {}", targetPath, e.getMessage());
            return null;
        }
    }
}
