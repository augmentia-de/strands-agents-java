package de.augmentia.strandsagents.vault;

public class SecretNotFoundException extends RuntimeException {
    public SecretNotFoundException(String message) {
        super(message);
    }

    public SecretNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
