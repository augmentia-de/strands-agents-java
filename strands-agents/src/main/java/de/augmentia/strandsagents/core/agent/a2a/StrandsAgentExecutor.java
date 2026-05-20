package de.augmentia.strandsagents.core.agent.a2a;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.model.event.AgentFinishedEvent;
import de.augmentia.strandsagents.core.model.event.ToolExecutionStartedEvent;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import de.augmentia.strandsagents.core.model.event.AgentEvent;
import java.util.List;
import java.util.concurrent.Flow;

/**
 * Implementation of the A2A {@link AgentExecutor} that wraps a Strands {@link Agent}.
 * This class translates A2A execution requests into Strands agent runs and maps
 * Strands internal events to A2A emitter updates.
 */
public class StrandsAgentExecutor implements AgentExecutor {
    private final Agent strandsAgent;

    public StrandsAgentExecutor(Agent strandsAgent) {
        this.strandsAgent = strandsAgent;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        String prompt = extractTextFromMessage(context.getMessage());
        
        // Initial setup for A2A task state if needed
        emitter.submit();
        emitter.startWork();

        strandsAgent.executeEvents(prompt, new Flow.Subscriber<AgentEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AgentEvent event) {
                if (event instanceof ToolExecutionStartedEvent toolEvent) {
                    emitter.startWork();
                    emitter.addArtifact(List.of(new TextPart("Executing tool: " + toolEvent.toolCall().toolName())));
                } else if (event instanceof AgentFinishedEvent finishedEvent) {
                    String answer = finishedEvent.finalAnswer();
                    emitter.complete(Message.builder()
                        .parts(List.of(new TextPart(answer)))
                        .build());
                } else {
                    emitter.addArtifact(List.of(new TextPart(event.toString())));
                }
            }

            @Override
            public void onError(Throwable throwable) {
                emitter.complete(Message.builder()
                    .parts(List.of(new TextPart("Fehler: " + throwable.getMessage())))
                    .build());
            }

            @Override
            public void onComplete() {
            }
        });
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        strandsAgent.cancel();
        emitter.cancel();
    }

    private String extractTextFromMessage(Message message) {
        StringBuilder textBuilder = new StringBuilder();
        if (message.parts() != null) {
            for (Part<?> part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    textBuilder.append(textPart.text());
                }
            }
        }
        return textBuilder.toString();
    }
}
