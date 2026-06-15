package de.augmentia.strandsagents.features.secrets;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
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

        var factory = findSecretProviderFactory(provider.trim());
        if (factory != null) {
            log.info("CloudSecretProvider: using SPI factory '{}' for provider '{}'",
                factory.getClass().getName(), provider);
            return factory.create(config);
        }

        log.warn("CloudSecretProvider: no SPI factory found for provider '{}'", provider);
        return null;
    }

    private static SecretProviderFactory findSecretProviderFactory(String type) {
        var serviceFile = "/META-INF/services/" + SecretProviderFactory.class.getName();
        try (var in = CloudSecretProviderFactory.class.getResourceAsStream(serviceFile)) {
            if (in == null) {
                log.debug("SPI file not found: {}", serviceFile);
                return null;
            }
            try (var reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    try {
                        var clazz = Class.forName(line);
                        if (SecretProviderFactory.class.isAssignableFrom(clazz)) {
                            var factory = (SecretProviderFactory) clazz.getDeclaredConstructor().newInstance();
                            if (factory.type().equalsIgnoreCase(type)) {
                                return factory;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to load SecretProviderFactory impl: {}", line, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read SPI file: {}", serviceFile, e);
        }
        return null;
    }
}
