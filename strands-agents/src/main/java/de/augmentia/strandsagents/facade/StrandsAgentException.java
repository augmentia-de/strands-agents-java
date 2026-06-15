package de.augmentia.strandsagents.facade;

public class StrandsAgentException extends RuntimeException {

    public StrandsAgentException(String message) {
        super(message);
    }

    public StrandsAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
