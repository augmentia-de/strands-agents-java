package de.augmentia.strandsagents.secrets.gcp;

import de.augmentia.strandsagents.features.secrets.SecretProvider;
import de.augmentia.strandsagents.features.secrets.SecretProviderFactory;
import java.util.Map;

public class GcpSecretProviderFactory implements SecretProviderFactory {

    @Override
    public String type() {
        return "gcp";
    }

    @Override
    public SecretProvider create(Map<String, String> config) {
        var gcpProject = config.getOrDefault("gcpProject", "");
        var gcpSecretId = config.getOrDefault("gcpSecretId", "");
        return new GcpSecretManagerProvider(
            gcpProject.isBlank() ? null : gcpProject,
            gcpSecretId.isBlank() ? null : gcpSecretId
        );
    }
}
