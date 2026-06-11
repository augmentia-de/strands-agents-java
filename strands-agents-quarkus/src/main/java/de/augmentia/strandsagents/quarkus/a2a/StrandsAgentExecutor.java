package de.augmentia.strandsagents.quarkus.a2a;

import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.quarkus.service.AgentService;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidParamsError;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.UnsupportedOperationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Demo implementation of the A2A {@link AgentExecutor} with a fixed, property-configured agent.
 *
 * <p><b>Demo notice:</b> This implementation creates a single fixed agent
 * ({@code a2a.tools}, {@code a2a.system-prompt}) and reuses it for all A2A tasks.
 * There is no per-session isolation or dynamic tool configuration per task.
 * For production use the executor should work session-based
 * (cf. {@code AgentService.initAgent()} / {@code AgentService.chat()}).</p>
 */
public class StrandsAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(StrandsAgentExecutor.class);

    private final AgentService agentService;
    private final StrandsA2AProperties props;

    private volatile Agent agent;

    public StrandsAgentExecutor(AgentService agentService, StrandsA2AProperties props) {
        this.agentService = agentService;
        this.props = props;
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) throws A2AError {
        if (!props.enabled()) {
            emitter.fail(new UnsupportedOperationError());
            return;
        }

        var agent = getOrCreateAgent();

        var userInput = ctx.getUserInput("\n");
        if (userInput.isBlank()) {
            emitter.fail(new InvalidParamsError("No text input found in message"));
            return;
        }

        emitter.startWork();
        try {
            var result = agent.execute(ctx.getTaskId(), userInput);
            emitter.addArtifact(List.of(new TextPart(result.finalAnswer())));
            emitter.complete();
        } catch (Exception e) {
            log.error("A2A agent execution failed: {}", e.getMessage(), e);
            emitter.fail(new InternalError("Agent execution failed: " + e.getMessage()));
        }
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) throws A2AError {
        log.info("A2A cancel requested for task {}", ctx.getTaskId());
        agentService.cancelExecution(ctx.getContextId());
        emitter.cancel();
    }

    private Agent getOrCreateAgent() {
        if (agent != null) return agent;
        synchronized (this) {
            if (agent != null) return agent;
            agent = createAgent();
            return agent;
        }
    }

    private Agent createAgent() {
        var toolNames = props.tools().isBlank()
            ? null
            : new HashSet<>(Arrays.asList(props.tools().split(",")));
        return agentService.createA2AAgent(props.systemPrompt(), toolNames);
    }
}
