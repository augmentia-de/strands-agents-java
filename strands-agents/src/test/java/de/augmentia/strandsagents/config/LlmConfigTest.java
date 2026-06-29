package de.augmentia.strandsagents.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.features.secrets.SecretProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmConfigTest {

    @Test
    void constructor_setsAllFields() {
        var cfg = new LlmConfig("sk-key", "https://api.openai.com", "gpt-4", 0.7, 3, null, null);
        assertThat(cfg.apiKey()).isEqualTo("sk-key");
        assertThat(cfg.baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(cfg.modelName()).isEqualTo("gpt-4");
        assertThat(cfg.temperature()).isEqualTo(0.7);
        assertThat(cfg.maxRetries()).isEqualTo(3);
    }

    @Test
    void constructor_allowsNulls() {
        var cfg = new LlmConfig(null, null, null, null, null, null, null);
        assertThat(cfg.apiKey()).isNull();
        assertThat(cfg.temperature()).isNull();
    }

    @Test
    void fromVault_usesVaultWithFallback() {
        var vault = new SecretProvider() {
            @Override
            public String getSecret(String path, String key) {
                return null;
            }

            @Override
            public Map<String, String> getSecrets(String path) {
                return Map.of("api_key", "vault-key", "model", "gpt-4-vault");
            }
        };
        System.setProperty("OPENAI_BASE_URL", "https://vault-fallback.openai.com");
        try {
            var cfg = LlmConfig.fromVault(vault, "/secret/openai");
            assertThat(cfg.apiKey()).isEqualTo("vault-key");
            assertThat(cfg.modelName()).isEqualTo("gpt-4-vault");
            assertThat(cfg.baseUrl()).isEqualTo("https://vault-fallback.openai.com");
            assertThat(cfg.temperature()).isNull();
        } finally {
            System.clearProperty("OPENAI_BASE_URL");
        }
    }

    @Test
    void fromVault_usesDefaultsWhenMissing() {
        var vault = new SecretProvider() {
            @Override
            public String getSecret(String path, String key) {
                return null;
            }

            @Override
            public Map<String, String> getSecrets(String path) {
                return Map.of("api_key", "key");
            }
        };
        var cfg = LlmConfig.fromVault(vault, "/secret/defaults");
        assertThat(cfg.apiKey()).isEqualTo("key");
        assertThat(cfg.baseUrl()).isNull();
        assertThat(cfg.modelName()).isNull();
    }
}
