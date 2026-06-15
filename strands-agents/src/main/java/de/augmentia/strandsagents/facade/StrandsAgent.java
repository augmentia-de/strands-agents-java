package de.augmentia.strandsagents.facade;

import java.util.function.Consumer;

public interface StrandsAgent {

    static StrandsAgentBuilder builder() {
        return new StrandsAgentBuilder();
    }

    String ask(String userMessage);

    String ask(String userMessage, String sessionId);

    void askStream(String userMessage, Consumer<String> tokenConsumer);

    void askStream(String userMessage, String sessionId, Consumer<String> tokenConsumer);
}
