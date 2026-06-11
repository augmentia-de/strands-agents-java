package de.augmentia.strandsagents.features.secrets;

import java.util.Map;

public interface SecretProviderFactory {
    String type();

    SecretProvider create(Map<String, String> config);
}
