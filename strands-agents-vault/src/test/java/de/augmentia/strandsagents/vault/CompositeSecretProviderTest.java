package de.augmentia.strandsagents.vault;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;

import de.augmentia.strandsagents.core.secret.SecretProvider;
import org.junit.jupiter.api.Test;

class CompositeSecretProviderTest {

    @Test
    void shouldFallbackToSecondProvider() {
        var failing = new SecretProvider() {
            public String getSecret(String path, String key) {
                throw new SecretNotFoundException("not found");
            }
            public Map<String, String> getSecrets(String path) {
                throw new SecretNotFoundException("not found");
            }
        };
        var working = new SecretProvider() {
            public String getSecret(String path, String key) { return "value"; }
            public Map<String, String> getSecrets(String path) { return Map.of("k", "v"); }
        };

        var composite = new CompositeSecretProvider(failing, working);
        assertThat(composite.getSecret("p", "k")).isEqualTo("value");
        assertThat(composite.getSecrets("p")).containsEntry("k", "v");
    }

    @Test
    void shouldThrowWhenAllProvidersFail() {
        var failing1 = new SecretProvider() {
            public String getSecret(String path, String key) {
                throw new SecretNotFoundException("nope");
            }
            public Map<String, String> getSecrets(String path) {
                throw new SecretNotFoundException("nope");
            }
        };
        var failing2 = new SecretProvider() {
            public String getSecret(String path, String key) {
                throw new SecretNotFoundException("nope2");
            }
            public Map<String, String> getSecrets(String path) {
                throw new SecretNotFoundException("nope2");
            }
        };

        var composite = new CompositeSecretProvider(failing1, failing2);
        assertThatThrownBy(() -> composite.getSecret("p", "k"))
            .isInstanceOf(SecretNotFoundException.class);
        assertThat(composite.getSecrets("p")).isEmpty();
    }

    @Test
    void shouldUseFirstProviderResult() {
        var provider = new CompositeSecretProvider(
            new FileSecretProvider(Path.of("nonexistent"), true),
            new FileSecretProvider(Path.of("nonexistent2"), true)
        );
        assertThat(provider.getSecrets("any")).isEmpty();
    }
}
