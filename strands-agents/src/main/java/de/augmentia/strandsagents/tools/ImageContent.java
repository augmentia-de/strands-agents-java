package de.augmentia.strandsagents.tools;

public record ImageContent(String base64, String mimeType) implements ContentBlock {
    @Override
    public String type() {
        return "image";
    }
}
