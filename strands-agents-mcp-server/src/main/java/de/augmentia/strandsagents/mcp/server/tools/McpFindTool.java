package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpFindTool {
    private static final Logger log = LoggerFactory.getLogger(McpFindTool.class);
    private static final int MAX_OUTPUT_BYTES = 50_000;
    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "target", ".venv", ".idea",
        "__pycache__", ".mvn", ".gradle", "build", "dist",
        ".next", ".vscode", ".sessions", "data", ".sass-cache",
        "coverage", ".nyc_output", ".cache", "tmp", "temp",
        "vendor", "bower_components", ".tox", " eggs", ".eggs",
        "site-packages", ".terraform", "Pods", ".serverless");
    private static final Set<String> SKIP_FILES = Set.of(
        ".DS_Store", "Thumbs.db", "desktop.ini");

    private final Path cwd;

    public McpFindTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Find files by glob pattern (e.g., *.java, src/**/*.ts). Skips common build/dependency directories.")
    public String find(
            @P("Glob pattern (e.g., *.java, src/**/*.ts)") String pattern,
            @P("Directory to search in (default: current directory)") String path,
            @P("Maximum number of results (default: 100)") Integer maxResults) {
        log.debug("find START pattern={}", pattern);
        var searchPath = path != null ? resolve(path) : cwd;
        var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        var limit = maxResults != null ? maxResults : 100;
        var results = new ArrayList<String>();
        var totalBytes = new int[1];

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (results.size() >= limit || totalBytes[0] >= MAX_OUTPUT_BYTES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (dir.equals(searchPath)) return FileVisitResult.CONTINUE;
                    var fileName = dir.getFileName().toString();
                    if (SKIP_DIRS.contains(fileName) || fileName.startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= limit || totalBytes[0] >= MAX_OUTPUT_BYTES) {
                        return FileVisitResult.TERMINATE;
                    }
                    var fileName = file.getFileName().toString();
                    if (SKIP_FILES.contains(fileName)) {
                        return FileVisitResult.CONTINUE;
                    }
                    var relPath = searchPath.relativize(file);
                    if (matcher.matches(relPath) || matcher.matches(file.getFileName())) {
                        results.add(relPath.toString());
                    }
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
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage());
        }

        var output = results.isEmpty()
            ? "No files found matching pattern: " + pattern
            : results.stream().sorted().collect(Collectors.joining("\n"));
        if (results.size() >= limit) {
            output += "\n\n[Results truncated at " + limit + " matches.]";
        } else if (totalBytes[0] >= MAX_OUTPUT_BYTES) {
            output += "\n\n[Output truncated at " + MAX_OUTPUT_BYTES + " bytes.]";
        }
        log.debug("find DONE results={}", results.size());
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
