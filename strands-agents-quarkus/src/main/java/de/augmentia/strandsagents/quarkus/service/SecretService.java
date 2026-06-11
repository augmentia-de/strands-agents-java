package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.features.secrets.SecretProvider;
import de.augmentia.strandsagents.features.secrets.CloudSecretProviderFactory;
import de.augmentia.strandsagents.features.secrets.VaultConfig;
import de.augmentia.strandsagents.features.secrets.VaultSecretProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private volatile String runtimeApiKey;
    private VaultSecretProvider vault;
    private SecretProvider cloudProvider;

    @PostConstruct
    void init() {
        var addr = System.getenv("VAULT_ADDR");
        var token = System.getenv("VAULT_TOKEN");
        if (addr != null && !addr.isBlank() && token != null && !token.isBlank()) {
            try {
                vault = new VaultSecretProvider(VaultConfig.fromEnv());
                log.info("Vault initialized at {}", addr);
            } catch (Exception e) {
                log.warn("Vault init failed, fallback to env vars: {}", e.getMessage());
                vault = null;
            }
        } else {
            log.info("VAULT_ADDR/VAULT_TOKEN not set – using env vars only");
        }

        var cloudProviderType = getConfig("STRANDS_SECRET_CLOUD_PROVIDER");
        if (cloudProviderType != null) {
            cloudProvider = CloudSecretProviderFactory.create(
                cloudProviderType,
                getConfig("STRANDS_SECRET_AWS_SSM_PATH"),
                getConfig("STRANDS_SECRET_GCP_PROJECT_ID"),
                getConfig("STRANDS_SECRET_GCP_SECRET_ID")
            );
            log.info("CloudSecretProvider: type={} active={}", cloudProviderType, cloudProvider != null);
        } else {
            log.info("STRANDS_SECRET_CLOUD_PROVIDER not set – no cloud secret provider");
        }
    }

    public void setRuntimeApiKey(String key) {
        this.runtimeApiKey = key;
    }

    public void clearRuntimeApiKey() {
        this.runtimeApiKey = null;
    }

    public boolean isRuntimeKeyActive() {
        return runtimeApiKey != null;
    }

    public String getOpenAiApiKey() {
        if (runtimeApiKey != null) {
            log.info("getOpenAiApiKey → runtimeApiKey ({})", mask(runtimeApiKey));
            return runtimeApiKey;
        }
        if (vault != null) {
            try {
                var val = vault.getSecret("openai", "api_key");
                if (val != null) {
                    log.info("getOpenAiApiKey → vault ({})", mask(val));
                    return val;
                }
            } catch (Exception e) {
                log.debug("Vault openai/api_key nicht gefunden: {}", e.getMessage());
            }
        }
        if (cloudProvider != null) {
            try {
                var val = cloudProvider.getSecret("openai", "api_key");
                if (val != null) {
                    log.info("getOpenAiApiKey → cloudSecretProvider ({})", mask(val));
                    return val;
                }
            } catch (Exception e) {
                log.debug("Cloud secret provider failed: {}", e.getMessage());
            }
        }
        var envVal = System.getenv("OPENAI_API_KEY");
        log.info("getOpenAiApiKey → env.OPENAI_API_KEY = {}", mask(envVal));
        return envVal;
    }

    public String getTavilyApiKey() {
        if (vault != null) {
            try {
                return vault.getSecret("tavily", "api_key");
            } catch (Exception e) {
                log.debug("Vault tavily/api_key nicht gefunden: {}", e.getMessage());
            }
        }
        return System.getenv("TAVILY_API_KEY");
    }

    public String getSecret(String envVar, String vaultPath, String vaultKey) {
        if (vault != null) {
            try {
                return vault.getSecret(vaultPath, vaultKey);
            } catch (Exception e) {
                log.debug("Vault {}/{} nicht gefunden: {}", vaultPath, vaultKey, e.getMessage());
            }
        }
        return System.getenv(envVar);
    }

    public boolean isVaultEnabled() {
        return vault != null;
    }

    private static String getConfig(String key) {
        var val = System.getenv(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        return null;
    }

    private static String mask(String s) {
        if (s == null) return null;
        if (s.length() <= 8) return s;
        return s.substring(0, 8) + "...";
    }
}
