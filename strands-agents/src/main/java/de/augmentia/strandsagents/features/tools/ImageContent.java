package de.augmentia.strandsagents.features.tools;

import de.augmentia.strandsagents.features.tools.ContentBlock;

public record ImageContent(String base64, String mimeType) implements ContentBlock {
    @Override
    public String type() {
        return "image";
    }
}
