package com.strands.agents.core.model.agent;

import com.strands.agents.core.model.message.Message;
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
