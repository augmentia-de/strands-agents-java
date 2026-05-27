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
    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "target", ".venv", ".idea",
        "__pycache__", ".mvn", ".gradle", "build", "dist",
        ".next", ".vscode", ".sessions", "data", ".sass-cache",
        "coverage", ".nyc_output", ".cache", "tmp", "temp",
        "vendor", "bower_components", ".tox", " eggs", ".eggs",
        "site-packages", ".terraform", "Pods", ".serverless");

    private final Path cwd;

    public McpLsTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("List directory contents. Use path for specific directory, recursive for subdirectories.")
    public String ls(
            @P("Directory to list (default: current directory)") String path,
            @P("List recursively (default: false)") Boolean recursive,
            @P("Maximum depth for recursive listing") Integer depth,
            @P("Show file size and date (default: false)") Boolean details,
            @P("Maximum number of results (default: 200)") Integer maxResults) {
        log.debug("ls START path={}", path);
        var target = path != null ? resolve(path) : cwd;

        if (!Files.exists(target)) throw new RuntimeException("Path does not exist: " + path);
        if (!Files.isDirectory(target)) throw new RuntimeException("Path is not a directory: " + path);

        var entries = new ArrayList<Path>();
        var limit = maxResults != null ? maxResults : 200;

        try {
            if (Boolean.TRUE.equals(recursive)) {
                int maxDepth = depth != null ? depth : Integer.MAX_VALUE;
                Files.walkFileTree(target, Set.of(FileVisitOption.FOLLOW_LINKS), maxDepth, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (entries.size() >= limit) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (dir.equals(target)) return FileVisitResult.CONTINUE;
                        var fileName = dir.getFileName().toString();
                        if (SKIP_DIRS.contains(fileName) || fileName.startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        entries.add(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (entries.size() >= limit) {
                            return FileVisitResult.TERMINATE;
                        }
                        entries.add(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        if (exc instanceof AccessDeniedException) {
                            log.warn("Skipping unreadable: {}", file);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
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
        var resolved = p.isAbsolute() ? p : cwd.resolve(p).normalize();
        if (!resolved.startsWith(cwd)) {
            throw new RuntimeException("Access denied: path outside working directory: " + path);
        }
        return resolved;
    }
}
