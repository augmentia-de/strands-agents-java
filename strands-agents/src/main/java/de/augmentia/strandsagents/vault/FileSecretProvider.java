package de.augmentia.strandsagents.features.secrets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.features.secrets.SecretProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileSecretProvider implements SecretProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;
    private final boolean createIfMissing;
    private Map<String, Map<String, String>> cache;

    public FileSecretProvider(Path filePath) {
        this(filePath, false);
    }

    public FileSecretProvider(Path filePath, boolean createIfMissing) {
        this.filePath = filePath;
        this.createIfMissing = createIfMissing;
    }

    @Override
    public String getSecret(String path, String key) {
        var secrets = getSecrets(path);
        var value = secrets.get(key);
        if (value == null)
            throw new SecretNotFoundException(
                "Key '" + key + "' not found at path '" + path + "' in " + filePath);
        return value;
    }

    @Override
    public Map<String, String> getSecrets(String path) {
        var store = load();
        var secrets = store.get(path);
        return secrets != null ? secrets : Map.of();
    }

    private Map<String, Map<String, String>> load() {
        if (cache != null) return cache;

        if (!Files.exists(filePath)) {
            if (createIfMissing) {
                cache = Map.of();
                return cache;
            }
            throw new SecretNotFoundException(
                "Secrets file not found: " + filePath);
        }

        try {
            var content = Files.readString(filePath);
            cache = MAPPER.readValue(content, new TypeReference<>() {});
            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read secrets file: " + filePath, e);
        }
    }

    public void setSecret(String path, String key, String value) {
        var store = new LinkedHashMap<>(load());
        var secrets = new LinkedHashMap<>(store.getOrDefault(path, Map.of()));
        secrets.put(key, value);
        store.put(path, secrets);
        persist(store);
        cache = store;
    }

    private void persist(Map<String, Map<String, String>> store) {
        try {
            var dir = filePath.getParent();
            if (dir != null && !Files.exists(dir))
                Files.createDirectories(dir);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), store);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write secrets file: " + filePath, e);
        }
    }
}
