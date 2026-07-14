package de.augmentia.strandsagents.config.secrets;

import java.util.Map;

@FunctionalInterface
/** Provides secrets from a secret store by path and key. */
public interface SecretProvider {
    String getSecret(String path, String key);

    /** Retrieves all secrets at a given path. */
    default Map<String, String> getSecrets(String path) {
        throw new UnsupportedOperationException("getSecrets not implemented");
    }
}
