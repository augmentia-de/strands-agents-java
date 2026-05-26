package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.vault.VaultConfig;
import de.augmentia.strandsagents.vault.VaultSecretProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private VaultSecretProvider vault;

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
    }

    public String getOpenAiApiKey() {
        if (vault != null) {
            try {
                return vault.getSecret("openai", "api_key");
            } catch (Exception e) {
                log.debug("Vault openai/api_key nicht gefunden: {}", e.getMessage());
            }
        }
        return System.getenv("OPENAI_API_KEY");
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
}
