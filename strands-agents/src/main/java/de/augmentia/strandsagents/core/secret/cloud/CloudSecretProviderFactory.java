package de.augmentia.strandsagents.core.secret.cloud;

import de.augmentia.strandsagents.core.secret.SecretProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudSecretProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(CloudSecretProviderFactory.class);

    private CloudSecretProviderFactory() {}

    public static SecretProvider create(String provider, String ssmPath, String gcpProject, String gcpSecretId) {
        if (provider == null || provider.isBlank()) {
            log.info("CloudSecretProvider: no provider configured");
            return null;
        }
        switch (provider.trim().toLowerCase()) {
            case "aws":
                if (ssmPath == null || ssmPath.isBlank()) {
                    log.warn("CloudSecretProvider: AWS SSM path not configured");
                    return null;
                }
                log.info("CloudSecretProvider: creating AWS SSM provider (path={})", ssmPath);
                return new AwsSsmProvider(ssmPath);

            case "gcp":
                if (gcpSecretId == null || gcpSecretId.isBlank()) {
                    log.warn("CloudSecretProvider: GCP secret ID not configured");
                    return null;
                }
                log.info("CloudSecretProvider: creating GCP Secret Manager provider (secret={}, project={})",
                    gcpSecretId, gcpProject != null ? gcpProject : "(auto-detect)");
                return new GcpSecretManagerProvider(gcpProject, gcpSecretId);

            default:
                log.warn("CloudSecretProvider: unknown provider '{}'", provider);
                return null;
        }
    }
}
