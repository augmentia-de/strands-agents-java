package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ToolResult(List<Object> content, Object details) {
    public static ToolResult success(String text) {
        return new ToolResult(List.of(text), null);
    }

    public static ToolResult success(String text, Object details) {
        return new ToolResult(List.of(text), details);
    }

    public static ToolResult error(String error) {
        return new ToolResult(List.of("[ERROR] " + error), error);
    }

    public static ToolResult json(JsonNode json) {
        return new ToolResult(List.of(new JsonContent(json)), null);
    }

    public static ToolResult mixed(String text, JsonNode json) {
        return new ToolResult(List.of(text, new JsonContent(json)), null);
    }
}
