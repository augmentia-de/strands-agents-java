package de.augmentia.strandsagents.core.security;

public enum CapabilityToken {
    FILE_READ,
    FILE_WRITE,
    DB_READ,
    DB_WRITE,
    NETWORK,
    EXECUTE,
    LLM_CALL,
    S3_READ,
    S3_WRITE,
    KAFKA_PUBLISH,
    KAFKA_CONSUME,
    VAULT_READ,
    VAULT_WRITE
}
