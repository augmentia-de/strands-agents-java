package de.augmentia.strandsagents.quarkus.agui.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunAgentInput {
    public String threadId;
    public String runId;
    public Map<String, Object> state;
    public List<AguiMessage> messages;
    public List<Map<String, Object>> tools;
    public List<?> context;
    public Map<String, Object> forwardedProps;
}
