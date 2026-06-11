package de.augmentia.strandsagents.model.agent;

import de.augmentia.strandsagents.model.message.Message;
import java.util.List;

public record AgentResult(
    String sessionId,
    String finalAnswer,
    List<Message> generatedMessages,
    ExecutionMetrics metrics,
    StopReason stopReason,
    String structuredOutput
) {
    public AgentResult(String sessionId, String finalAnswer, List<Message> generatedMessages,
                       ExecutionMetrics metrics, StopReason stopReason) {
        this(sessionId, finalAnswer, generatedMessages, metrics, stopReason, null);
    }
}
