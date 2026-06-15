package de.augmentia.strandsagents.facade;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.StreamingAgent;
import de.augmentia.strandsagents.model.agent.AgentResult;
import java.util.function.Consumer;

public class DefaultStrandsAgent implements StrandsAgent {

    private final Agent agent;
    private final StreamingAgent streamingAgent;
    private final String name;

    public DefaultStrandsAgent(Agent agent, String name) {
        this.agent = agent;
        this.streamingAgent = agent instanceof StreamingAgent sa ? sa : null;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Agent getDelegate() {
        return agent;
    }

    public de.augmentia.strandsagents.core.AgentRunConfig getRunConfig() {
        return agent.getRunConfig();
    }

    @Override
    public String ask(String userMessage) {
        return resultToString(agent.execute(userMessage));
    }

    @Override
    public String ask(String userMessage, String sessionId) {
        return resultToString(agent.execute(sessionId, userMessage));
    }

    @Override
    public void askStream(String userMessage, Consumer<String> tokenConsumer) {
        if (streamingAgent != null) {
            streamingAgent.executeStreaming(userMessage, tokenConsumer);
            return;
        }
        var result = agent.execute(userMessage);
        if (result != null && result.finalAnswer() != null) {
            tokenConsumer.accept(result.finalAnswer());
        }
    }

    @Override
    public void askStream(String userMessage, String sessionId, Consumer<String> tokenConsumer) {
        if (streamingAgent != null) {
            streamingAgent.executeStreaming(sessionId, userMessage, tokenConsumer);
            return;
        }
        agent.setSessionId(sessionId);
        var result = agent.execute(userMessage);
        if (result != null && result.finalAnswer() != null) {
            tokenConsumer.accept(result.finalAnswer());
        }
    }

    private static String resultToString(AgentResult result) {
        if (result == null) return null;
        return result.structuredOutput() != null
            ? result.structuredOutput()
            : result.finalAnswer();
    }
}
