package de.augmentia.strandsagents.tools;

import de.augmentia.strandsagents.tools.builtin.ReadTool;

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
            List.of("Read image file [" + mimeType + "]", base64),
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
