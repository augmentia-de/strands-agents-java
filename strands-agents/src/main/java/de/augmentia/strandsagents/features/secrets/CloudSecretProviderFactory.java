package de.augmentia.strandsagents.features.secrets;

import java.util.Map;
import java.util.ServiceLoader;
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

        var config = new java.util.LinkedHashMap<String, String>();
        config.put("ssmPath", ssmPath != null ? ssmPath : "");
        config.put("gcpProject", gcpProject != null ? gcpProject : "");
        config.put("gcpSecretId", gcpSecretId != null ? gcpSecretId : "");

        // Try SPI-discovered factories first
        for (var factory : ServiceLoader.load(SecretProviderFactory.class)) {
            if (factory.type().equalsIgnoreCase(provider.trim())) {
                log.info("CloudSecretProvider: using SPI factory '{}' for provider '{}'",
                    factory.getClass().getName(), provider);
                return factory.create(config);
            }
        }

        log.warn("CloudSecretProvider: no SPI factory found for provider '{}'", provider);
        return null;
    }
}
