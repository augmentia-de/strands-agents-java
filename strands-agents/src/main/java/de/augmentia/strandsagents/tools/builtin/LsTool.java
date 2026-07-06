package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.TextContent;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LsTool implements AgentTool<LsTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(LsTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));
    private final FileSandboxGuard sandboxGuard;

    // Konstruktor-basierte Konfiguration analog zu ReadTool
    public LsTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.LS;
    }

    @Override
    public String description() {
        return "List directory contents. Returns JSON with directory, totalEntries, entries array (name, type, size, modified). "
            + "Use path for specific directory, recursive for subdirectories.";
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
        addStr(props, "path", "Directory relative to workspace root (default: workspace root)");
        addBool(props, "recursive", "List recursively (default: false)");
        addInt(props, "depth", "Maximum depth for recursive listing (default: no limit)");
        addBool(props, "details", "Show file size and date (default: false)");
        return schema;
    }

    private void addStr(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "string");
        node.put("description", d);
    }

    private void addBool(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "boolean");
        node.put("description", d);
    }

    private void addInt(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "integer");
        node.put("description", d);
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: ls START path={} recursive={} depth={}", params.path(), params.recursive(), params.depth());
        if (abortFlag.get()) {
            log.debug("Tool: ls ABORTED");
            throw new RuntimeException("Operation aborted");
        }

        Path targetPath = params.path() != null ? Path.of(params.path) : sandboxGuard.getWorkspaceRoot();

        Path secureDir;
        try {
            secureDir = sandboxGuard.validateAndResolve(targetPath.toString());
        } catch (Exception e) {
            return ToolResult.error("Security violation: " + e.getMessage());
        }



        if (!Files.exists(targetPath)) {
            throw new RuntimeException("Path does not exist: " + params.path());
        }
        if (!Files.isDirectory(targetPath)) {
            throw new RuntimeException("Path is not a directory: " + params.path());
        }

        var entries = new ArrayList<Path>();
        try {
            if (Boolean.TRUE.equals(params.recursive())) {
                int maxDepth = params.depth() != null ? params.depth() : Integer.MAX_VALUE;
                try (var stream = Files.walk(targetPath, maxDepth)) {
                    stream.skip(1).forEach(entries::add);
                }
            } else {
                try (var stream = Files.list(targetPath)) {
                    stream.forEach(entries::add);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory: " + e.getMessage());
        }

        entries.sort(Comparator.comparing(Path::toString));

        var root = MAPPER.createObjectNode();
        root.put("directory", targetPath.toString());

        var arr = root.putArray("entries");
        boolean details = Boolean.TRUE.equals(params.details());
        for (var entry : entries) {
            if (abortFlag.get()) {
                break;
            }
            var name = targetPath.relativize(entry).toString();
            var obj = arr.addObject();
            obj.put("name", Files.isDirectory(entry) ? name + "/" : name);
            obj.put("type", Files.isDirectory(entry) ? "dir" : "file");
            if (details) {
                try {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    obj.put("size", attrs.size());
                    obj.put("modified", ISO_FORMAT.format(attrs.lastModifiedTime().toInstant()));
                } catch (IOException ignored) {
                }
            }
        }

        root.put("totalEntries", arr.size());
        root.put("details", details);

        var output = arr.isEmpty()
            ? MAPPER.createObjectNode()
                .put("directory", targetPath.toString())
                .put("totalEntries", 0)
                .put("details", details)
                .put("empty", true)
                .toString()
            : root.toString();

        log.debug("Tool: ls DONE entries={}", entries.size());
        return new ToolResult(
            List.of(new TextContent(output)),
            new LsDetails(entries.size(), targetPath.toString()));
    }

    public record Params(String path, Boolean recursive, Boolean details, Integer depth) {}
    public record LsDetails(int entryCount, String directory) {}
}
