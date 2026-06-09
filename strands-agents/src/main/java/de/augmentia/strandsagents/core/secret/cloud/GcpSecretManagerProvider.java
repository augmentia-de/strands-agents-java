package de.augmentia.strandsagents.core.secret.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.secret.SecretProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public class GcpSecretManagerProvider implements SecretProvider {

    private static final Logger log = LoggerFactory.getLogger(GcpSecretManagerProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String METADATA_SERVER = "http://metadata.google.internal";
    private static final String SECRET_MANAGER_API = "https://secretmanager.googleapis.com";
    private static final String DEFAULT_API_VERSION = "v1";

    private final HttpClient client;
    private final String projectId;
    private final String secretId;
    private final String apiVersion;

    public GcpSecretManagerProvider(String projectId, String secretId) {
        this(projectId, secretId, DEFAULT_API_VERSION);
    }

    public GcpSecretManagerProvider(String projectId, String secretId, String apiVersion) {
        this.projectId = projectId;
        this.secretId = secretId;
        this.apiVersion = apiVersion != null ? apiVersion : DEFAULT_API_VERSION;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        log.info("GcpSecretManagerProvider: projectId={} secretId={}", this.projectId, this.secretId);
    }

    @Override
    public String getSecret(String path, String key) {
        var targetSecret = secretId;
        if (targetSecret == null || targetSecret.isBlank()) {
            targetSecret = key;
        }
        var targetProject = projectId;
        if (targetProject == null || targetProject.isBlank()) {
            try {
                targetProject = fetchProjectId();
            } catch (Exception e) {
                log.warn("GcpSecretManagerProvider: cannot detect project ID: {}", e.getMessage());
                return null;
            }
        }
        try {
            var accessToken = fetchAccessToken();
            if (accessToken == null) return null;

            var url = String.format("%s/%s/projects/%s/secrets/%s/versions/latest:access",
                SECRET_MANAGER_API, apiVersion, targetProject, targetSecret);
            log.info("GcpSecretManagerProvider: fetching secret {} from project {}", targetSecret, targetProject);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("GcpSecretManagerProvider: API returned {}: {}", response.statusCode(), response.body());
                return null;
            }

            var root = MAPPER.readValue(response.body(), Map.class);
            var payload = (Map<String, Object>) root.get("payload");
            if (payload == null) return null;
            var dataB64 = (String) payload.get("data");
            if (dataB64 == null) return null;
            var value = new String(Base64.getDecoder().decode(dataB64), StandardCharsets.UTF_8);
            if (!value.isBlank()) {
                log.info("GcpSecretManagerProvider: found secret {}", targetSecret);
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("GcpSecretManagerProvider: failed to read {}: {}", targetSecret, e.getMessage());
            return null;
        }
    }

    private String fetchAccessToken() throws Exception {
        var url = METADATA_SERVER + "/computeMetadata/v1/instance/service-accounts/default/token";
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Metadata-Flavor", "Google")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("GcpSecretManagerProvider: metadata token endpoint returned {}", response.statusCode());
            return null;
        }
        var root = MAPPER.readValue(response.body(), Map.class);
        return (String) root.get("access_token");
    }

    private String fetchProjectId() throws Exception {
        var url = METADATA_SERVER + "/computeMetadata/v1/project/project-id";
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Metadata-Flavor", "Google")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        return response.body().strip();
    }
}
