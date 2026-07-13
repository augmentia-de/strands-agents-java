package de.augmentia.strandsagents.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import de.augmentia.strandsagents.tools.builtin.ReadTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextFileReader implements FileReader {
    private static final Logger log = LoggerFactory.getLogger(TextFileReader.class);
    private static final int MAX_CHARS = 20_000;
    private static final int BINARY_SCAN_SIZE = 8192;

    @Override
    public String name() {
        return "text";
    }

    @Override
    public boolean supports(Path path) {
        try {
            var contentType = Files.probeContentType(path);
            if (contentType != null) {
                if (contentType.startsWith("text/")) return true;
                var supportedImages = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
                if (supportedImages.contains(contentType)) return false;
                return false;
            }
        } catch (IOException e) {
            log.debug("Could not probe content type for {}: {}", path, e.getMessage());
        }

        var n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".yaml")
            || n.endsWith(".yml") || n.endsWith(".md") || n.endsWith(".txt")
            || n.endsWith(".csv") || n.endsWith(".tsv") || n.endsWith(".log")
            || n.endsWith(".properties") || n.endsWith(".conf") || n.endsWith(".cfg")
            || n.endsWith(".sh") || n.endsWith(".bat") || n.endsWith(".cmd")) {
            return true;
        }

        var binaryExtensions = Set.of(".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".zip", ".tar", ".gz", ".bz2", ".7z", ".rar",
            ".exe", ".dll", ".so", ".dylib", ".class", ".jar",
            ".mp3", ".mp4", ".avi", ".mov", ".mkv",
            ".ttf", ".otf", ".woff", ".woff2");
        for (var ext : binaryExtensions) {
            if (n.endsWith(ext)) return false;
        }

        return !containsBinaryContent(path);
    }

    private boolean containsBinaryContent(Path path) {
        try (InputStream is = Files.newInputStream(path)) {
            var buf = new byte[BINARY_SCAN_SIZE];
            var bytesRead = is.read(buf);
            if (bytesRead > 0) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buf[i] == 0x00) return true;
                }
            }
        } catch (IOException e) {
            log.debug("Binary scan failed for {}: {}", path, e.getMessage());
        }
        return false;
    }

    @Override
    public ToolResult read(Path path, ReadTool.Params params) throws IOException {
        var lines = Files.readAllLines(path);
        var total = lines.size();

        Integer effectiveStartLine = params.offset();
        if (effectiveStartLine == null) effectiveStartLine = params.line_start();

        var start = effectiveStartLine != null ? Math.max(0, effectiveStartLine - 1) : 0;
        if (start >= lines.size() && total > 0) {
            throw new IOException("Offset beyond file end");
        }

        int end;
        if (params.line_end() != null) {
            end = Math.min(params.line_end(), lines.size());
        } else if (params.limit() != null) {
            end = Math.min(start + params.limit(), lines.size());
        } else {
            end = lines.size();
        }

        if (end < start) end = start;

        var hasExplicitRange = params.offset() != null || params.line_start() != null
            || params.limit() != null || params.line_end() != null;

        if (hasExplicitRange) {
            var sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(lines.get(i)).append("\n");
            }
            return ToolResult.success(sb.toString());
        }

        var sb = new StringBuilder();
        var charsOut = 0;
        for (var line : lines) {
            var toAppend = line + "\n";
            if (charsOut + toAppend.length() > MAX_CHARS) {
                var remaining = MAX_CHARS - charsOut;
                if (remaining > 0) {
                    sb.append(toAppend, 0, Math.min(remaining, toAppend.length()));
                }
                sb.append("\n[Truncated at ")
                    .append(MAX_CHARS).append(" chars — use limit=N to read more]");
                return ToolResult.success(sb.toString());
            }
            sb.append(toAppend);
            charsOut += toAppend.length();
        }

        return ToolResult.success(sb.toString());
    }
}
