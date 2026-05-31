package de.augmentia.strandsagents.core.tools.local;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.TextContent;
import de.augmentia.strandsagents.core.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LsTool implements AgentTool<LsTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(LsTool.class);
    private final Path cwd;

    public LsTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String description() {
        return "List directory contents. Use path for specific directory, recursive for subdirectories.";
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
        addStr(props, "path", "Directory to list (default: current directory)");
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

        var targetPath = params.path() != null ? resolve(params.path()) : cwd;

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

        var sb = new StringBuilder();
        for (var entry : entries) {
            if (abortFlag.get()) {
                break;
            }
            var name = targetPath.relativize(entry).toString();
            if (Boolean.TRUE.equals(params.details())) {
                try {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    var size = attrs.size();
                    var isDir = attrs.isDirectory() ? "d" : "-";
                    sb.append(isDir).append(" ");
                    sb.append(String.format("%10d", size)).append(" ");
                    sb.append(name);
                    if (isDir.equals("d")) {
                        sb.append("/");
                    }
                    sb.append("\n");
                } catch (IOException ignored) {
                    sb.append(name).append("\n");
                }
            } else {
                sb.append(name);
                if (Files.isDirectory(entry)) {
                    sb.append("/");
                }
                sb.append("\n");
            }
        }

        var output = sb.isEmpty()
            ? "(empty directory)"
            : sb.toString().trim();

        log.debug("Tool: ls DONE entries={}", entries.size());
        return new ToolResult(
            List.of(new TextContent(output)),
            new LsDetails(entries.size(), targetPath.toString()));
    }

    private Path resolve(String path) {
        try {
            var p = Paths.get(path);
            var resolved = (p.isAbsolute() ? p : cwd.resolve(p)).normalize().toAbsolutePath();
            var canonical = cwd.toRealPath();
            if (!resolved.startsWith(canonical)) {
                throw new RuntimeException("Access denied: path outside working directory: " + path);
            }
            return resolved;
        } catch (IOException e) {
            throw new RuntimeException("Access denied: path outside working directory: " + path);
        }
    }

    public record Params(String path, Boolean recursive, Boolean details, Integer depth) {}
    public record LsDetails(int entryCount, String directory) {}
}
