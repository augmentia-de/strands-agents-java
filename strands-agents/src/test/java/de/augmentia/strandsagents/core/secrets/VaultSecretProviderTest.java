package de.augmentia.strandsagents.core.secrets;

import static org.assertj.core.api.Assertions.*;

import de.augmentia.strandsagents.config.vault.SecretNotFoundException;
import de.augmentia.strandsagents.config.vault.VaultSecretProvider;
import de.augmentia.strandsagents.config.vault.VaultConfig;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

@Testcontainers
class VaultSecretProviderTest {

    private static final String VAULT_TOKEN = "myroot";

    @Container
    static VaultContainer<?> vault = new VaultContainer<>("hashicorp/vault:1.18")
        .withVaultToken(VAULT_TOKEN)
        .withSecretInVault("secret/test",
            "api_key=sk-test123",
            "base_url=https://api.openai.com");

    @Test
    void shouldReadSecretFromVaultKvV2() {
        var config = new de.augmentia.strandsagents.config.vault.VaultConfig(
            "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
            VAULT_TOKEN,
            "secret", 5000, 5000);

        var provider = new VaultSecretProvider(config);
        assertThat(provider.getSecret("test", "api_key")).isEqualTo("sk-test123");
        assertThat(provider.getSecret("test", "base_url")).isEqualTo("https://api.openai.com");
    }

    @Test
    void shouldThrowWhenPathNotFound() {
        var config = new de.augmentia.strandsagents.config.vault.VaultConfig(
            "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
            VAULT_TOKEN);

        var provider = new VaultSecretProvider(config);
        assertThatThrownBy(() -> provider.getSecret("nonexistent", "key"))
            .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void shouldThrowWhenKeyNotFound() {
        var config = new VaultConfig(
            "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
            VAULT_TOKEN,
            "secret", 5000, 5000);

        var provider = new VaultSecretProvider(config);
        assertThatThrownBy(() -> provider.getSecret("test", "missing_key"))
            .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void shouldGetAllSecretsAtPath() {
        var config = new VaultConfig(
            "http://" + vault.getHost() + ":" + vault.getMappedPort(8200),
            VAULT_TOKEN,
            "secret", 5000, 5000);

        var provider = new VaultSecretProvider(config);
        var secrets = provider.getSecrets("test");
        assertThat(secrets)
            .containsEntry("api_key", "sk-test123")
            .containsEntry("base_url", "https://api.openai.com");
    }
}
