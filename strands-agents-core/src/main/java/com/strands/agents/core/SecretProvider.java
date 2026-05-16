package com.strands.agents.core;

import java.util.Map;

@FunctionalInterface
public interface SecretProvider {
    String getSecret(String path, String key);

    default Map<String, String> getSecrets(String path) {
        throw new UnsupportedOperationException("getSecrets not implemented");
    }
}
