package de.augmentia.strandsagents.model.api;

import de.augmentia.strandsagents.model.agent.StopReason;
import java.util.List;
import java.util.Map;

public class ChatResponse {
    public String answer;
    public String sessionId;
    public StopReason stopReason;
    public long durationMs;
    public int inputTokens;
    public int outputTokens;
    public int toolCallsCount;
    public List<ToolCallInfo> toolCalls;
    public boolean memoryUsed;
    public List<String> memorySources;
    public List<String> phases;
    public String thinking;
    public String error;

    public static class ToolCallInfo {
        public String name;
        public Map<String, Object> arguments;
        public String result;
        public long durationMs;
        public boolean success;
    }
}
