package com.strands.agents.vault;

public record VaultConfig(
    String address,
    String token,
    String mountPath,
    int connectTimeoutMs,
    int readTimeoutMs
) {
    public static final String DEFAULT_ADDRESS = "http://127.0.0.1:8200";
    public static final String DEFAULT_MOUNT_PATH = "secret";
    public static final int DEFAULT_TIMEOUT_MS = 5000;

    public VaultConfig {
        if (address == null || address.isBlank())
            address = DEFAULT_ADDRESS;
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("Vault token must not be empty");
        if (mountPath == null || mountPath.isBlank())
            mountPath = DEFAULT_MOUNT_PATH;
        if (connectTimeoutMs <= 0)
            connectTimeoutMs = DEFAULT_TIMEOUT_MS;
        if (readTimeoutMs <= 0)
            readTimeoutMs = DEFAULT_TIMEOUT_MS;
    }

    public VaultConfig(String address, String token) {
        this(address, token, null, 0, 0);
    }

    public static VaultConfig fromEnv() {
        return new VaultConfig(
            System.getenv("VAULT_ADDR"),
            System.getenv("VAULT_TOKEN"),
            System.getenv("VAULT_MOUNT_PATH"),
            0, 0
        );
    }
}
