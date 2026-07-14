package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.*;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the contents of files matching a glob pattern securely inside the sandboxed workspace.
 */
public class ReadTool implements AgentTool<ReadTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private static final int MAX_FILES = 20;

    private final FileSandboxGuard sandboxGuard;
    private final FileReaderFactory readerFactory;

    public ReadTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
        this.readerFactory = FileReaderFactory.withDefaults();
    }

    public ReadTool(Path cwd, FileReaderFactory readerFactory) {
        try {
            this.sandboxGuard = new FileSandboxGuard(cwd.toString());
            this.readerFactory = readerFactory;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
    }

    @Override
    public String name() {
        return BaseToolNames.READ_FILES;
    }

    @Override
    public String description() {
        return "Reads the contents of files matching a glob pattern securely inside the sandbox.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public ObjectNode parameterSchema() {
        var schema = SCHEMA_MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        addStr(props, "path", "Glob pattern matching file paths relative to workspace root (e.g. **/*.java, src/**/Read*.java, or a literal path like src/main/Foo.java). Supports * (any filename), ** (any directory tree), and ? (single char).");
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

    /**
     * Executes the read operation: resolves the glob pattern, reads matching files, and returns their content.
     *
     * @param toolCallId unique identifier for this tool invocation
     * @param params     the read parameters (path, offset, limit, etc.)
     * @param abortFlag  flag to signal premature cancellation
     * @param onUpdate   callback for streaming intermediate results
     * @return the tool result containing file contents or error details
     */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.path() == null || params.path().isBlank()) {
            return ToolResult.json(errorNode("No pattern provided."));
        }

        var root = sandboxGuard.getWorkspaceRoot();
        var matcher = root.getFileSystem().getPathMatcher("glob:" + params.path());

        var matchedFiles = new ArrayList<Path>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> matcher.matches(root.relativize(p)))
                .forEach(matchedFiles::add);
        }

        var result = SCHEMA_MAPPER.createObjectNode();
        result.put("pattern", params.path());
        result.put("matched", matchedFiles.size());

        var files = result.putArray("files");
        var limit = Math.min(matchedFiles.size(), MAX_FILES);
        for (int i = 0; i < limit; i++) {
            if (abortFlag != null && abortFlag.get()) {
                result.put("aborted", true);
                break;
            }
            addFileToResult(matchedFiles.get(i), root, params, files);
        }

        if (matchedFiles.size() > MAX_FILES) {
            result.put("truncated", true);
        }

        if (matchedFiles.isEmpty()) {
            result.put("error", "No files found matching: " + params.path());
        }

        return ToolResult.json(result);
    }

    private void addFileToResult(Path absolutePath, Path root, Params params, ArrayNode files) {
        var fileObj = SCHEMA_MAPPER.createObjectNode();
        var relativePath = root.relativize(absolutePath).toString();
        fileObj.put("path", relativePath);

        try {
            sandboxGuard.validateAndResolve(relativePath);

            var reader = readerFactory.findReader(absolutePath);
            var readerResult = reader.read(absolutePath, params);
            var contentList = readerResult.content();

            long size = 0;
            try { size = Files.size(absolutePath); } catch (IOException ignored) {}
            fileObj.put("size", size);

            if ("image".equals(reader.name())) {
                fileObj.put("type", "image");
                if (!contentList.isEmpty()) fileObj.put("description", String.valueOf(contentList.get(0)));
                if (contentList.size() > 1) fileObj.put("base64", String.valueOf(contentList.get(1)));
            } else if ("fallback".equals(reader.name()) && isBinaryResult(contentList)) {
                fileObj.put("type", "binary");
                for (var entry : contentList) {
                    var s = String.valueOf(entry);
                    var eq = s.indexOf('=');
                    if (eq > 0) {
                        fileObj.put(s.substring(0, eq), s.substring(eq + 1));
                    }
                }
            } else {
                fileObj.put("type", reader.name());
                var content = contentList.isEmpty() ? "" : String.valueOf(contentList.get(0));
                fileObj.put("content", content);
                fileObj.put("lines", content.split("\n", -1).length);
                if (params.offset() != null) fileObj.put("offset", params.offset());
                if (params.limit() != null) fileObj.put("limit", params.limit());
            }

            files.add(fileObj);
        } catch (Exception e) {
            fileObj.put("error", "Error reading " + relativePath + ": " + e.getMessage());
            files.add(fileObj);
        }
    }

    private static boolean isBinaryResult(List<Object> content) {
        return !content.isEmpty() && "type=binary".equals(String.valueOf(content.get(0)));
    }

    private ObjectNode errorNode(String message) {
        var node = SCHEMA_MAPPER.createObjectNode();
        node.put("error", message);
        return node;
    }

    /**
     * Parameters for reading files: a glob path, optional offset/limit/line_start/line_end for partial reads.
     */
    public record Params(String path, Integer offset, Integer limit, Integer line_start, Integer line_end) {}
}
