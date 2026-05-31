package de.augmentia.strandsagents.core.tools.local;

import de.augmentia.strandsagents.core.tools.ContentBlock;

public record ImageContent(String base64, String mimeType) implements ContentBlock {
    @Override
    public String type() {
        return "image";
    }
}
