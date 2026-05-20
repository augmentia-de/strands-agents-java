package de.augmentia.strandsagents.vault;


import de.augmentia.strandsagents.core.secret.SecretProvider;

import java.util.List;
import java.util.Map;

public class CompositeSecretProvider implements SecretProvider {

    private final List<SecretProvider> providers;

    public CompositeSecretProvider(SecretProvider... providers) {
        this.providers = List.of(providers);
    }

    public CompositeSecretProvider(List<SecretProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public String getSecret(String path, String key) {
        for (var provider : providers) {
            try {
                return provider.getSecret(path, key);
            } catch (Exception ignored) {}
        }
        throw new SecretNotFoundException(
            "Key '" + key + "' not found at path '" + path + "' in any provider");
    }

    @Override
    public Map<String, String> getSecrets(String path) {
        for (var provider : providers) {
            try {
                return provider.getSecrets(path);
            } catch (Exception ignored) {}
        }
        return Map.of();
    }
}
