package de.augmentia.strandsagents.vault;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.SecretProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class VaultSecretProvider implements SecretProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KV_V2_PATH = "/v1/%s/data/%s";
    private static final String KV_V1_PATH = "/v1/%s/%s";

    private final HttpClient client;
    private final VaultConfig config;

    public VaultSecretProvider(VaultConfig config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.connectTimeoutMs()))
            .build();
    }

    @Override
    public String getSecret(String path, String key) {
        var secrets = getSecrets(path);
        var value = secrets.get(key);
        if (value == null)
            throw new SecretNotFoundException("Key '" + key + "' not found at path '" + path + "'");
        return value;
    }

    @Override
    public Map<String, String> getSecrets(String path) {
        try {
            return tryKvV2(path);
        } catch (Exception e) {
            try {
                return tryKvV1(path);
            } catch (Exception ex) {
                throw new SecretNotFoundException(
                    "Failed to read secrets from '" + path + "': " + ex.getMessage(), ex);
            }
        }
    }

    private Map<String, String> tryKvV2(String path) throws Exception {
        var url = config.address() + KV_V2_PATH.formatted(config.mountPath(), path);
        var response = httpGet(url);
        return extractKvV2Data(response);
    }

    private Map<String, String> tryKvV1(String path) throws Exception {
        var url = config.address() + KV_V1_PATH.formatted(config.mountPath(), path);
        var response = httpGet(url);
        return extractData(response);
    }

    private String httpGet(String url) throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("X-Vault-Token", config.token())
            .header("Accept", "application/json")
            .timeout(Duration.ofMillis(config.readTimeoutMs()))
            .GET()
            .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404)
            throw new SecretNotFoundException("Path not found: " + url);
        if (response.statusCode() != 200)
            throw new RuntimeException("Vault API error: " + response.statusCode() + " " + response.body());

        return response.body();
    }

    private Map<String, String> extractKvV2Data(String json) throws Exception {
        var root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        var data = castMap(root.get("data"));
        var secretData = castMap(data.get("data"));
        return toStringMap(secretData);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object obj) {
        if (obj instanceof Map<?, ?> m)
            return (Map<String, Object>) m;
        return Map.of();
    }

    private Map<String, String> extractData(String json) throws Exception {
        var root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        var data = castMap(root.get("data"));
        return toStringMap(data);
    }

    private Map<String, String> toStringMap(Map<String, Object> data) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : data.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
        return result;
    }
}
