package de.augmentia.strandsagents.core.agent.a2a;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.agent.ExecutionMetrics;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A client-side implementation of the Strands {@link Agent} that communicates with a remote 
 * A2A agent via the official A2A Java SDK.
 * This allows a remote agent to be used within the Strands framework like a local model-based agent.
 */
public class A2AClientAgent extends Agent {
    private final Client a2aClient;
    private final AgentCard card;

    public A2AClientAgent(String endpoint) {
        super(null, null, null); 
        this.card = new A2ACardResolver(endpoint).getAgentCard();
        
        this.a2aClient = Client.builder(card)
            .addConsumers(List.of((event, agentCard) -> handleEvent(event)))
            .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
            .build();
    }

    private CompletableFuture<String> currentResponse;

    private void handleEvent(ClientEvent event) {
        if (event instanceof MessageEvent messageEvent && currentResponse != null) {
            Message responseMessage = messageEvent.getMessage();
            StringBuilder textBuilder = new StringBuilder();
            if (responseMessage.parts() != null) {
                for (Part<?> part : responseMessage.parts()) {
                    if (part instanceof TextPart textPart) {
                        textBuilder.append(textPart.text());
                    }
                }
            }
            currentResponse.complete(textBuilder.toString());
        }
    }

    @Override
    public AgentResult execute(String prompt, Map<String, Object> context) {
        currentResponse = new CompletableFuture<>();
        long start = System.currentTimeMillis();
        
        Message request = Message.builder()
            .parts(List.of(new TextPart(prompt)))
            .build();
        
        try {
            a2aClient.sendMessage(request);
            String answer = currentResponse.join();
            long duration = System.currentTimeMillis() - start;
            
            return new AgentResult(
                getSessionId(),
                answer,
                List.of(),
                new ExecutionMetrics(duration, 0, 0, 0),
                StopReason.COMPLETED
            );
        } catch (Exception e) {
            throw new RuntimeException("A2A call failed", e);
        }
    }
}
