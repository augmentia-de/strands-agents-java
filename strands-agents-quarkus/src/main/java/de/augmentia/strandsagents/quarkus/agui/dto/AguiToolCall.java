package de.augmentia.strandsagents.quarkus.agui.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AguiToolCall {
    public String id;
    public String name;
    public Map<String, Object> arguments;
}
