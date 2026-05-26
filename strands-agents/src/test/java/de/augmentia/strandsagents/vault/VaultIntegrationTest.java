package de.augmentia.strandsagents.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
class VaultIntegrationTest {

    private static final String ROOT_TOKEN = "root-token-123";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Container
    static GenericContainer<?> vault = new GenericContainer<>(DockerImageName.parse("hashicorp/vault:1.18"))
        .withExposedPorts(8200)
        .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
        .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200));

    private static String vaultAddr;

    @BeforeAll
    static void setup() {
        vaultAddr = "http://" + vault.getHost() + ":" + vault.getMappedPort(8200);
    }

    @Test
    void testCompleteVaultLifecycle() throws Exception {
        // 1. Prepare Configuration
        var config = new VaultConfig(vaultAddr, ROOT_TOKEN, "secret", 5000, 5000);
        var provider = new VaultSecretProvider(config);

        // 2. Write a secret directly via Vault API (KV v2)
        writeSecretKvV2("openai", Map.of(
            "api_key", "sk-live-123456",
            "model", "gpt-4o"
        ));

        // 3. Read it back via Provider
        assertThat(provider.getSecret("openai", "api_key")).isEqualTo("sk-live-123456");
        assertThat(provider.getSecret("openai", "model")).isEqualTo("gpt-4o");

        // 4. Test getSecrets (Map)
        Map<String, String> allSecrets = provider.getSecrets("openai");
        assertThat(allSecrets).hasSize(2)
            .containsEntry("api_key", "sk-live-123456")
            .containsEntry("model", "gpt-4o");
    }

    @Test
    void testKvV1Fallback() throws Exception {
        // Enable KV v1 at path 'legacy'
        enableKvV1("legacy");
        writeSecretKvV1("legacy", "old-app", Map.of("password", "secret123"));

        var config = new VaultConfig(vaultAddr, ROOT_TOKEN, "legacy", 5000, 5000);
        var provider = new VaultSecretProvider(config);

        // Should fall back to KV v1 when KV v2 (data/ path) fails
        assertThat(provider.getSecret("old-app", "password")).isEqualTo("secret123");
    }

    @Test
    void testErrorHandling() throws Exception {
        var config = new VaultConfig(vaultAddr, ROOT_TOKEN, "secret", 0, 0);
        var provider = new VaultSecretProvider(config);

        assertThatThrownBy(() -> provider.getSecret("nonexistent", "key"))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining("Path not found");

        writeSecretKvV2("broken", Map.of("existing", "value"));
        assertThatThrownBy(() -> provider.getSecret("broken", "missing-key"))
            .isInstanceOf(SecretNotFoundException.class)
            .hasMessageContaining("Key 'missing-key' not found");
    }

    private void writeSecretKvV2(String path, Map<String, String> data) throws Exception {
        var body = Map.of("data", data);
        var json = MAPPER.writeValueAsString(body);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(vaultAddr + "/v1/secret/data/" + path))
            .header("X-Vault-Token", ROOT_TOKEN)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() > 299) {
            throw new RuntimeException("Failed to write KV v2: " + response.body());
        }
    }

    private void enableKvV1(String path) throws Exception {
        var body = Map.of("type", "kv", "options", Map.of("version", "1"));
        var json = MAPPER.writeValueAsString(body);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(vaultAddr + "/v1/sys/mounts/" + path))
            .header("X-Vault-Token", ROOT_TOKEN)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() > 299 && response.statusCode() != 400) { // 400 if already exists
             throw new RuntimeException("Failed to enable KV v1: " + response.body());
        }
    }

    private void writeSecretKvV1(String mount, String path, Map<String, String> data) throws Exception {
        var json = MAPPER.writeValueAsString(data);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(vaultAddr + "/v1/" + mount + "/" + path))
            .header("X-Vault-Token", ROOT_TOKEN)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() > 299) {
            throw new RuntimeException("Failed to write KV v1: " + response.body());
        }
    }
}
