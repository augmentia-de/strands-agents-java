package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpLsTool {
    private static final Logger log = LoggerFactory.getLogger(McpLsTool.class);
    private final Path cwd;

    public McpLsTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("List directory contents. Use path for specific directory, recursive for subdirectories.")
    public String ls(
            @P("Directory to list (default: current directory)") String path,
            @P("List recursively (default: false)") Boolean recursive,
            @P("Maximum depth for recursive listing") Integer depth,
            @P("Show file size and date (default: false)") Boolean details) {
        log.debug("ls START path={}", path);
        var target = path != null ? resolve(path) : cwd;

        if (!Files.exists(target)) throw new RuntimeException("Path does not exist: " + path);
        if (!Files.isDirectory(target)) throw new RuntimeException("Path is not a directory: " + path);

        var entries = new ArrayList<Path>();
        try {
            if (Boolean.TRUE.equals(recursive)) {
                int maxDepth = depth != null ? depth : Integer.MAX_VALUE;
                try (var stream = Files.walk(target, maxDepth)) {
                    stream.skip(1).forEach(entries::add);
                }
            } else {
                try (var stream = Files.list(target)) {
                    stream.forEach(entries::add);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to list directory: " + e.getMessage());
        }

        entries.sort(Comparator.comparing(Path::toString));
        var sb = new StringBuilder();

        for (var entry : entries) {
            var name = target.relativize(entry).toString();
            if (Boolean.TRUE.equals(details)) {
                try {
                    var attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    sb.append(attrs.isDirectory() ? "d " : "- ");
                    sb.append(String.format("%10d ", attrs.size()));
                    sb.append(name);
                    if (attrs.isDirectory()) sb.append("/");
                    sb.append("\n");
                } catch (IOException ignored) {
                    sb.append(name).append("\n");
                }
            } else {
                sb.append(name);
                if (Files.isDirectory(entry)) sb.append("/");
                sb.append("\n");
            }
        }

        var output = sb.isEmpty() ? "(empty directory)" : sb.toString().trim();
        log.debug("ls DONE entries={}", entries.size());
        return output;
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }
}
