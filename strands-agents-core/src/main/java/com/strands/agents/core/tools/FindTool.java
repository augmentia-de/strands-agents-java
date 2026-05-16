package com.strands.agents.core.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindTool implements AgentTool<FindTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(FindTool.class);

    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "target", ".venv", ".idea",
        "__pycache__", ".mvn", ".gradle", "build", "dist",
        ".next", ".vscode", ".sessions", "data", ".sass-cache",
        "coverage", ".nyc_output", ".cache", "tmp", "temp",
        "vendor", "bower_components", ".tox", " eggs", ".eggs",
        "site-packages", ".terraform", "Pods", ".serverless"
    );

    private static final Set<String> SKIP_FILES = Set.of(
        ".DS_Store", "Thumbs.db", "desktop.ini"
    );

    private final Path cwd;

    public FindTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "find";
    }

    @Override
    public String description() {
        return "Find files by glob pattern (e.g., *.java, src/**/*.ts). Skips common build/ dependency directories.";
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
        addStr(props, "pattern", "Glob pattern (e.g., *.java, src/**/*.ts)");
        addStr(props, "path", "Directory to search in (default: current directory)");
        addInt(props, "maxResults", "Maximum number of results (default: 100)");
        schema.putArray("required").add("pattern");
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
        log.debug("Tool: find START pattern={} path={}", params.pattern(), params.path());
        if (abortFlag.get()) {
            log.debug("Tool: find ABORTED");
            throw new RuntimeException("Operation aborted");
        }

        var searchPath = params.path() != null ? resolve(params.path()) : cwd;
        var matcher = FileSystems.getDefault().getPathMatcher("glob:" + params.pattern());
        var maxResults = params.maxResults() != null ? params.maxResults() : 100;
        var results = new ArrayList<String>();

        try {
            try (var stream = Files.walk(searchPath)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(p, searchPath))
                    .filter(p -> {
                        var relPath = searchPath.relativize(p);
                        return matcher.matches(relPath) || matcher.matches(p.getFileName());
                    })
                    .forEach(p -> {
                        if (abortFlag.get() || results.size() >= maxResults) return;
                        results.add(searchPath.relativize(p).toString());
                    });
            }
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage());
        }

        var output = results.isEmpty()
            ? "No files found matching pattern: " + params.pattern()
            : results.stream().sorted().collect(Collectors.joining("\n"));

        if (results.size() >= maxResults) {
            output += "\n\n[Results truncated at " + maxResults + " matches.]";
        }

        log.debug("Tool: find DONE results={}", results.size());
        return new ToolResult(
            List.of(new TextContent(output)),
            new FindDetails(results.size(), params.pattern()));
    }

    private boolean isSkipped(Path path, Path root) {
        var fileName = path.getFileName().toString();
        if (SKIP_FILES.contains(fileName)) {
            return true;
        }
        for (var parent : path) {
            if (SKIP_DIRS.contains(parent.toString())) {
                return true;
            }
        }
        var relative = root.relativize(path).toString().replace(java.io.File.separatorChar, '/');
        return relative.startsWith(".");
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }

    public record Params(String pattern, String path, Integer maxResults) {}
    public record FindDetails(int fileCount, String pattern) {}
}
