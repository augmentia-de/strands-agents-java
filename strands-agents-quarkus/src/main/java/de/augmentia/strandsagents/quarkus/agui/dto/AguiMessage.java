package de.augmentia.strandsagents.quarkus.agui.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AguiMessage {
    public String id;
    public String role;
    public String content;
    public List<AguiToolCall> toolCalls;
    public String toolCallId;
}
