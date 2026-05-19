package de.augmentia.strandsagents.quarkus.dto;

import de.augmentia.strandsagents.core.model.agent.StopReason;
import java.util.List;

public class ChatResponse {
    public String answer;
    public String sessionId;
    public StopReason stopReason;
    public long durationMs;
    public int inputTokens;
    public int outputTokens;
    public int toolCalls;
    public List<String> phases;
    public String error;
}
