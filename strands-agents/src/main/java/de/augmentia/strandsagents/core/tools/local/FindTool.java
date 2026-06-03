package de.augmentia.strandsagents.core.tools.local;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.augmentia.strandsagents.core.internal.WorkspacePaths;
import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.TextContent;
import de.augmentia.strandsagents.core.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FindTool implements AgentTool<FindTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(FindTool.class);
    private static final int MAX_OUTPUT_BYTES = 50_000;

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

    private final WorkspacePaths workspacePaths;

    public FindTool(Path cwd) {
        try {
            this.workspacePaths = new WorkspacePaths(cwd);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
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

        var searchPath = params.path() != null ? workspacePaths.resolve(params.path()) : workspacePaths.workspace();
        var matcher = FileSystems.getDefault().getPathMatcher("glob:" + params.pattern());
        var maxResults = params.maxResults() != null ? params.maxResults() : 100;
        var results = new ArrayList<String>();
        var totalBytes = new int[1];

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (abortFlag.get() || results.size() >= maxResults || totalBytes[0] >= MAX_OUTPUT_BYTES) {
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
                    if (abortFlag.get() || results.size() >= maxResults || totalBytes[0] >= MAX_OUTPUT_BYTES) {
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
            ? "No files found matching pattern: " + params.pattern()
            : results.stream().sorted().collect(Collectors.joining("\n"));

        if (results.size() >= maxResults) {
            output += "\n\n[Results truncated at " + maxResults + " matches.]";
        } else if (totalBytes[0] >= MAX_OUTPUT_BYTES) {
            output += "\n\n[Output truncated at " + MAX_OUTPUT_BYTES + " bytes.]";
        }

        log.debug("Tool: find DONE results={}", results.size());
        return new ToolResult(
            List.of(new TextContent(output)),
            new FindDetails(results.size(), params.pattern()));
    }

    public record Params(String pattern, String path, Integer maxResults) {}
    public record FindDetails(int fileCount, String pattern) {}
}
