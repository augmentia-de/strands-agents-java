package de.augmentia.strandsagents.features.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.features.internal.WorkspacePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadTool implements AgentTool<ReadTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(ReadTool.class);
    private final WorkspacePaths workspacePaths;
    private final FileReaderFactory readerFactory;

    public ReadTool(Path cwd) {
        this(cwd, FileReaderFactory.withDefaults());
    }

    public ReadTool(Path cwd, FileReaderFactory readerFactory) {
        try {
            this.workspacePaths = new WorkspacePaths(cwd);
            this.readerFactory = readerFactory;
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
        return "Read the contents of a file. Supported formats: text files, images (.jpg/.png/.gif/.webp), PDFs (.pdf). "
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
        addStr(props, "path", "Path to the file relative to workspace root");
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

        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                var listing = files
                    .map(p -> p.getFileName().toString() + (Files.isDirectory(p) ? "/" : ""))
                    .sorted()
                    .toList();
                var sb = new StringBuilder();
                sb.append("Directory: ").append(params.path()).append("\n");
                for (var f : listing) {
                    sb.append("  ").append(f).append("\n");
                }
                return ToolResult.success(sb.toString());
            } catch (IOException e) {
                throw new RuntimeException("Failed to list directory: " + params.path(), e);
            }
        }

        try {
            var reader = readerFactory.findReader(path);
            if (reader == null) {
                return ToolResult.error("Unsupported file type: " + params.path()
                    + " — use a plain text file (.txt, .json, .md, etc.),"
                    + " an image (.jpg, .png, .gif, .webp),"
                    + " or a PDF (.pdf).");
            }
            return reader.read(path, params);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record Params(String path, Integer offset, Integer limit, Integer line_start, Integer line_end) {}
}
