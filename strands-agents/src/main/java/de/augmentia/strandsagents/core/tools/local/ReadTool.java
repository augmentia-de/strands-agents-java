package de.augmentia.strandsagents.core.tools.local;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.core.internal.WorkspacePaths;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.TextContent;
import de.augmentia.strandsagents.core.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadTool implements AgentTool<ReadTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(ReadTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private final WorkspacePaths workspacePaths;

    public ReadTool(Path cwd) {
        try {
            this.workspacePaths = new WorkspacePaths(cwd);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String description() {
        return "Read the contents of a file. Supports text files and images. "
            + "Use offset/limit for large files.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public ObjectNode parameterSchema() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        addStr(props, "path", "Path to the file to read (relative or absolute)");
        addInt(props, "offset", "Line number to start reading from (1-indexed)");
        addInt(props, "line_start", "Line number to start reading from (1-indexed, alias for offset)");
        addInt(props, "limit", "Maximum number of lines to read");
        addInt(props, "line_end", "Line number to end reading at (inclusive)");
        schema.putArray("required").add("path");
        return schema;
    }

    private void addStr(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "string");
        node.put("description", d);
    }

    private void addInt(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "integer");
        node.put("description", d);
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: read START path={}", params.path());
        if (abortFlag.get()) {
            throw new RuntimeException("Operation aborted");
        }

        var path = workspacePaths.resolve(params.path());
        if (!Files.isReadable(path)) {
            throw new RuntimeException("File not readable: " + params.path());
        }

        try {
            var mimeType = detectImageMimeType(path);
            if (mimeType != null) {
                var bytes = Files.readAllBytes(path);
                var base64 = Base64.getEncoder().encodeToString(bytes);
                return new ToolResult(
                    List.of(new TextContent("Read image file [" + mimeType + "]"), new ImageContent(base64, mimeType)),
                    null);
            }

            var lines = Files.readAllLines(path);
            var total = lines.size();

            // Handle offset / line_start
            Integer effectiveStartLine = params.offset();
            if (effectiveStartLine == null) effectiveStartLine = params.line_start();
            
            var start = effectiveStartLine != null ? Math.max(0, effectiveStartLine - 1) : 0;
            if (start >= lines.size() && total > 0) {
                throw new IOException("Offset beyond file end");
            }

            // Handle limit / line_end
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
            var truncated = false;

            for (var line : selected) {
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

            return new ToolResult(List.of(new TextContent(sb.toString())), null);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String detectImageMimeType(Path path) {
        var n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".gif")) {
            return "image/gif";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    public record Params(String path, Integer offset, Integer limit, Integer line_start, Integer line_end) {}
}
