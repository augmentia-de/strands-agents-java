package com.strands.agents.vault;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSecretProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadSecretFromFile() throws IOException {
        var file = tempDir.resolve("secrets.json");
        Files.writeString(file, """
            {"myapp":{"api_key":"sk-123","base_url":"https://example.com"}}
            """);

        var provider = new FileSecretProvider(file);
        assertThat(provider.getSecret("myapp", "api_key")).isEqualTo("sk-123");
        assertThat(provider.getSecret("myapp", "base_url")).isEqualTo("https://example.com");
    }

    @Test
    void shouldThrowWhenFileNotFound() {
        var provider = new FileSecretProvider(tempDir.resolve("nonexistent.json"));
        assertThatThrownBy(() -> provider.getSecret("x", "y"))
            .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void shouldCreateFileWhenMissingWithFlag() {
        var file = tempDir.resolve("new.json");
        var provider = new FileSecretProvider(file, true);
        assertThat(provider.getSecrets("test")).isEmpty();
    }

    @Test
    void shouldThrowWhenKeyNotFound() throws IOException {
        var file = tempDir.resolve("secrets.json");
        Files.writeString(file, "{\"myapp\":{\"api_key\":\"sk-123\"}}");

        var provider = new FileSecretProvider(file);
        assertThatThrownBy(() -> provider.getSecret("myapp", "missing"))
            .isInstanceOf(SecretNotFoundException.class);
    }

    @Test
    void shouldReturnEmptyMapForUnknownPath() throws IOException {
        var file = tempDir.resolve("secrets.json");
        Files.writeString(file, "{\"myapp\":{\"api_key\":\"sk-123\"}}");

        var provider = new FileSecretProvider(file);
        assertThat(provider.getSecrets("unknown")).isEmpty();
    }

    @Test
    void shouldWriteAndReadBack() {
        var file = tempDir.resolve("writable.json");
        var provider = new FileSecretProvider(file, true);
        provider.setSecret("myapp", "api_key", "sk-456");
        provider.setSecret("myapp", "base_url", "https://vault.example.com");

        assertThat(provider.getSecret("myapp", "api_key")).isEqualTo("sk-456");
        assertThat(provider.getSecret("myapp", "base_url")).isEqualTo("https://vault.example.com");
    }
}
