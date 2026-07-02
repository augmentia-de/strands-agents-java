package de.augmentia.strandsagents.tools;

import java.util.List;

public record ToolResult(List<ContentBlock> content, Object details) {
    public static ToolResult success(String text) {
        return new ToolResult(List.of(new TextContent(text)), null);
    }

    public static ToolResult success(String text, Object details) {
        return new ToolResult(List.of(new TextContent(text)), details);
    }

    public static ToolResult error(String error) {
        return new ToolResult(List.of(new TextContent("[ERROR] " + error)), error);
    }
}
