package de.augmentia.strandsagents.features.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public class ImageFileReader implements FileReader {
    @Override
    public String name() {
        return "image";
    }

    @Override
    public boolean supports(Path path) {
        return detectImageMimeType(path) != null;
    }

    @Override
    public ToolResult read(Path path, ReadTool.Params params) throws IOException {
        var mimeType = detectImageMimeType(path);
        var bytes = Files.readAllBytes(path);
        var base64 = Base64.getEncoder().encodeToString(bytes);
        return new ToolResult(
            List.of(new TextContent("Read image file [" + mimeType + "]"), new ImageContent(base64, mimeType)),
            null);
    }

    private String detectImageMimeType(Path path) {
        var n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        return null;
    }
}
