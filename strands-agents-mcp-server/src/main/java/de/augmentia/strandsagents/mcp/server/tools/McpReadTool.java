package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpReadTool {
    private static final Logger log = LoggerFactory.getLogger(McpReadTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private final Path cwd;

    public McpReadTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Read the contents of a file. Supports text files and images. Use offset/limit for large files.")
    public String read(
            @P("Path to the file to read (relative or absolute)") String path,
            @P("Line number to start reading from (1-indexed)") Integer offset,
            @P("Maximum number of lines to read") Integer limit,
            @P("Line number to start reading from (1-indexed, alias for offset)") Integer lineStart,
            @P("Line number to end reading at (inclusive)") Integer lineEnd) {
        log.debug("read START path={}", path);
        var resolved = resolve(path);
        if (!Files.isReadable(resolved)) {
            throw new RuntimeException("File not readable: " + path);
        }
        try {
            var mimeType = detectImage(resolved);
            if (mimeType != null) {
                var bytes = Files.readAllBytes(resolved);
                return "[Image: " + mimeType + " (" + bytes.length + " bytes, base64: "
                    + Base64.getEncoder().encodeToString(bytes).substring(0, 100) + "...)]";
            }
            var lines = Files.readAllLines(resolved);
            var total = lines.size();

            Integer effectiveStart = offset;
            if (effectiveStart == null) effectiveStart = lineStart;
            var start = effectiveStart != null ? Math.max(0, effectiveStart - 1) : 0;
            if (start >= total && total > 0) {
                throw new IOException("Offset beyond file end");
            }

            int end;
            if (lineEnd != null) {
                end = Math.min(lineEnd, total);
            } else if (limit != null) {
                end = Math.min(start + limit, total);
            } else {
                end = total;
            }
            if (end < start) end = start;

            var sb = new StringBuilder();
            var outLines = 0;
            var outBytes = 0L;
            var truncated = false;

            for (var line : lines.subList(start, end)) {
                var lb = line.getBytes().length + 1;
                if (outLines >= MAX_LINES || outBytes + lb > MAX_BYTES) {
                    truncated = true;
                    break;
                }
                sb.append(line).append("\n");
                outLines++;
                outBytes += lb;
            }
            if (truncated) {
                sb.append("\n[Truncated. Total lines: ").append(total).append("]");
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String detectImage(Path path) {
        var n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        return null;
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        var resolved = p.isAbsolute() ? p : cwd.resolve(p).normalize();
        if (!resolved.startsWith(cwd)) {
            throw new RuntimeException("Access denied: path outside working directory: " + path);
        }
        return resolved;
    }
}
