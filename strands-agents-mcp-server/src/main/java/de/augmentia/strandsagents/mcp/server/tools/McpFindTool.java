package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpFindTool {
    private static final Logger log = LoggerFactory.getLogger(McpFindTool.class);
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

        try (var stream = Files.walk(searchPath)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> !isSkipped(p, searchPath))
                .filter(p -> {
                    var rel = searchPath.relativize(p);
                    return matcher.matches(rel) || matcher.matches(p.getFileName());
                })
                .forEach(p -> {
                    if (results.size() >= limit) return;
                    results.add(searchPath.relativize(p).toString());
                });
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage());
        }

        var output = results.isEmpty()
            ? "No files found matching pattern: " + pattern
            : results.stream().sorted().collect(Collectors.joining("\n"));
        if (results.size() >= limit) {
            output += "\n\n[Results truncated at " + limit + " matches.]";
        }
        log.debug("find DONE results={}", results.size());
        return output;
    }

    private boolean isSkipped(Path path, Path root) {
        var fileName = path.getFileName().toString();
        if (SKIP_FILES.contains(fileName)) return true;
        for (var p : path) {
            if (SKIP_DIRS.contains(p.toString())) return true;
        }
        return root.relativize(path).toString().replace(java.io.File.separatorChar, '/').startsWith(".");
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }
}
