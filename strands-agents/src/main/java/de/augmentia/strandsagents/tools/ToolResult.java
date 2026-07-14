package de.augmentia.strandsagents.tools;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Holds the content and optional details produced by a tool execution.
 */
public record ToolResult(List<Object> content, Object details) {
    /**
     * Creates a successful result with the given text content.
     */
    public static ToolResult success(String text) {
        return new ToolResult(List.of(text), null);
    }

    /**
     * Creates a successful result with text and additional details.
     */
    public static ToolResult success(String text, Object details) {
        return new ToolResult(List.of(text), details);
    }

    /**
     * Creates an error result wrapping the error message.
     */
    public static ToolResult error(String error) {
        return new ToolResult(List.of("[ERROR] " + error), error);
    }

    /**
     * Creates a result containing a JSON node as content.
     */
    public static ToolResult json(JsonNode json) {
        return new ToolResult(List.of(new JsonContent(json)), null);
    }

    /**
     * Creates a result with both text and JSON content.
     */
    public static ToolResult mixed(String text, JsonNode json) {
        return new ToolResult(List.of(text, new JsonContent(json)), null);
    }
}
