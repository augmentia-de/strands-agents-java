package de.augmentia.strandsagents.tools;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import de.augmentia.strandsagents.tools.builtin.ReadTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextFileReader implements FileReader {
    private static final Logger log = LoggerFactory.getLogger(TextFileReader.class);
    private static final int MAX_LINES = 200;
    private static final int MAX_BYTES = 51_200;
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

        List<String> selected = lines.subList(start, end);

        var sb = new StringBuilder();
        var outLines = 0;
        var outBytes = 0L;
        var truncatedLines = false;
        var truncatedBytes = false;

        for (var line : selected) {
            var lb = line.getBytes().length + 1;
            if (outLines >= MAX_LINES) {
                truncatedLines = true;
                break;
            }
            if (outBytes + lb > MAX_BYTES) {
                truncatedBytes = true;
                break;
            }
            sb.append(line).append("\n");
            outLines++;
            outBytes += lb;
        }

        if (truncatedLines || truncatedBytes) {
            sb.append("\n[Truncated: read ")
                .append(outLines).append(" of ").append(total).append(" lines")
                .append(" (").append(outBytes / 1024).append("KB of ")
                .append(Files.size(path) / 1024).append("KB)")
                .append(" — limit is ").append(MAX_LINES).append(" lines / ")
                .append(MAX_BYTES / 1024).append("KB]");
        }

        return ToolResult.success(sb.toString());
    }
}
