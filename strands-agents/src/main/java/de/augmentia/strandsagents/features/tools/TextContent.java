package de.augmentia.strandsagents.features.tools;

public record TextContent(String text) implements ContentBlock {
    @Override
    public String type() {
        return "text";
    }

    @Override
    public String toString() {
        return text;
    }
}
