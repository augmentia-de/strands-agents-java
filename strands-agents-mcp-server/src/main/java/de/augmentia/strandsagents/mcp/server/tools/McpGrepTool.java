package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpGrepTool {
    private static final Logger log = LoggerFactory.getLogger(McpGrepTool.class);
    private static final int MAX_LINE_CHARS = 500;
    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "target", ".venv", ".idea",
        "__pycache__", ".mvn", ".gradle", "build", "dist",
        ".next", ".vscode", ".sessions", "data", ".sass-cache",
        "coverage", ".nyc_output", ".cache", "tmp", "temp",
        "vendor", "bower_components", ".tox", " eggs", ".eggs",
        "site-packages", ".terraform", "Pods", ".serverless");
    private static final Set<String> BINARY_EXTS = Set.of(
        ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".webp",
        ".pdf", ".zip", ".tar", ".gz", ".exe", ".so", ".dll", ".wasm",
        ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".mp3",
        ".mp4", ".avi", ".mov", ".bin", ".dat", ".db", ".sqlite");

    private final Path cwd;

    public McpGrepTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Search file contents for a regex pattern. Skips common build directories and binary files.")
    public String grep(
            @P("Regex pattern to search for") String pattern,
            @P("File glob pattern to include (e.g., *.java)") String include,
            @P("File or directory to search in (default: current directory)") String path,
            @P("Whether search is case-sensitive (default: false)") Boolean caseSensitive,
            @P("Maximum number of results (default: 50)") Integer maxResults) {
        log.debug("grep START pattern={}", pattern);
        var searchPath = path != null ? resolve(path) : cwd;
        var flags = Boolean.TRUE.equals(caseSensitive) ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, flags);
        } catch (Exception e) {
            throw new RuntimeException("Invalid regex: " + e.getMessage());
        }

        PathMatcher includeMatcher = include != null
            ? FileSystems.getDefault().getPathMatcher("glob:" + include) : null;
        var limit = maxResults != null ? maxResults : 50;
        var results = new ArrayList<String>();

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (results.size() >= limit) {
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
                    if (results.size() >= limit) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (isBinary(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (includeMatcher != null
                        && !includeMatcher.matches(file.getFileName())
                        && !includeMatcher.matches(searchPath.relativize(file))) {
                        return FileVisitResult.CONTINUE;
                    }
                    try (var lineStream = Files.lines(file)) {
                        var lineNum = new int[1];
                        lineStream.forEach(line -> {
                            if (results.size() >= limit) return;
                            lineNum[0]++;
                            if (compiled.matcher(line).find()) {
                                var truncated = line.length() > MAX_LINE_CHARS
                                    ? line.substring(0, MAX_LINE_CHARS) + "..."
                                    : line;
                                results.add(searchPath.relativize(file) + ":" + lineNum[0] + ":" + truncated);
                            }
                        });
                    } catch (IOException ignored) {}
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
            ? "No matches found for pattern: " + pattern
            : String.join("\n", results);
        if (results.size() >= limit) {
            output += "\n\n[Results truncated at " + limit + " matches.]";
        }
        log.debug("grep DONE matches={}", results.size());
        return output;
    }

    private boolean isBinary(Path path) {
        var name = path.getFileName().toString().toLowerCase();
        return BINARY_EXTS.stream().anyMatch(name::endsWith);
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
