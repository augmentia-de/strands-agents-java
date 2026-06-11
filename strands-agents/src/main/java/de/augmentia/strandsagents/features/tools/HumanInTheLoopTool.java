package de.augmentia.strandsagents.features.tools;

import de.augmentia.strandsagents.features.hitl.checkpoint.CheckpointService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HumanInTheLoopTool {

    private static final Logger log = LoggerFactory.getLogger(HumanInTheLoopTool.class);

    private final CheckpointService checkpointService;

    public HumanInTheLoopTool(CheckpointService checkpointService) {
        this.checkpointService = checkpointService != null ? checkpointService : new CheckpointService();
    }

    @Tool("Asks the human user for input, clarification, or approval to proceed.")
    public String askUser(@P("The message or question to display to the user") String message) {
        var sessionId = de.augmentia.strandsagents.features.context.AgentContext.SESSION.get();
        var sid = sessionId != null
            ? String.valueOf(sessionId.get("sessionId"))
            : "unknown";
        var cp = checkpointService.createCheckpoint(sid, "askUser", message);
        log.info("HITL checkpoint created: {} — waiting for user input", cp.id());

        try {
            var resolved = checkpointService.await(cp);
            if (resolved.status() == de.augmentia.strandsagents.features.hitl.checkpoint.Checkpoint.Status.APPROVED) {
                var response = resolved.feedback() != null ? resolved.feedback() : "";
                log.info("HITL checkpoint {} — user responded: {}", cp.id(), response);
                return response;
            } else {
                var reason = resolved.feedback() != null ? resolved.feedback() : "No response";
                log.info("HITL checkpoint {} — rejected: {}", cp.id(), reason);
                return "[User declined to respond: " + reason + "]";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "[HITL interrupted]";
        }
    }
}
