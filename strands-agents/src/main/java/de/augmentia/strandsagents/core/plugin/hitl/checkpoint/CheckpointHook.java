package de.augmentia.strandsagents.core.plugin.hitl.checkpoint;

import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.hook.AgentHook;
import de.augmentia.strandsagents.core.hook.HookContexts;
import de.augmentia.strandsagents.core.hook.HookResult;
import de.augmentia.strandsagents.core.model.agent.AgentPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckpointHook implements AgentHook {

    private static final Logger log = LoggerFactory.getLogger(CheckpointHook.class);

    private final CheckpointService service;
    private volatile Agent agent;

    public CheckpointHook(CheckpointService service) {
        this.service = service;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    @Override
    public String name() {
        return "checkpoint-hitl";
    }

    @Override
    public HookResult beforeToolCall(HookContexts.BeforeToolCallContext ctx) {
        if (!service.requiresApproval(ctx.toolName())) {
            return new HookResult.Continue();
        }

        var args = ctx.arguments() != null ? ctx.arguments().toString() : "{}";
        var cp = service.createCheckpoint(ctx.sessionId(), ctx.toolName(), args);
        log.info("Checkpoint created: {} for tool '{}' in session '{}'", cp.id(), ctx.toolName(), ctx.sessionId());

        if (agent != null) {
            agent.pauseExecution();
        }

        try {
            var resolved = service.await(cp);
            if (resolved.status() == Checkpoint.Status.APPROVED) {
                log.info("Checkpoint {} approved — continuing tool '{}'", cp.id(), ctx.toolName());
                if (agent != null) {
                    agent.approve();
                }
                return new HookResult.Continue();
            } else {
                var reason = resolved.feedback() != null ? resolved.feedback() : "Rejected via HITL checkpoint";
                log.info("Checkpoint {} rejected — cancelling tool '{}': {}", cp.id(), ctx.toolName(), reason);
                if (agent != null) {
                    agent.reject(reason);
                }
                return new HookResult.Cancel(reason);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HookResult.Cancel("Interrupted while waiting for HITL approval");
        }
    }
}
