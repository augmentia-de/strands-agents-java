package com.strands.agents.core.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrepTool implements AgentTool<GrepTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(GrepTool.class);

    private static final Set<String> SKIP_DIRS = Set.of(
        ".git", "node_modules", "target", ".venv", ".idea",
        "__pycache__", ".mvn", ".gradle", "build", "dist",
        ".next", ".vscode", ".sessions", "data", ".sass-cache",
        "coverage", ".nyc_output", ".cache", "tmp", "temp",
        "vendor", "bower_components", ".tox", " eggs", ".eggs",
        "site-packages", ".terraform", "Pods", ".serverless"
    );

    private static final Set<String> BINARY_EXTS = Set.of(
        ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".webp",
        ".pdf", ".zip", ".tar", ".gz", ".exe", ".so", ".dll", ".wasm",
        ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".mp3",
        ".mp4", ".avi", ".mov", ".bin", ".dat", ".db", ".sqlite"
    );

    private final Path cwd;

    public GrepTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents for a regex pattern. Skips common build directories and binary files. "
            + "Returns matching lines in file:path:line format.";
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
        addStr(props, "pattern", "Regex pattern to search for");
        addStr(props, "include", "File glob pattern to include (e.g., *.java)");
        addStr(props, "path", "File or directory to search in (default: current directory)");
        addBool(props, "caseSensitive", "Whether search is case-sensitive (default: false)");
        addInt(props, "maxResults", "Maximum number of results (default: 50)");
        schema.putArray("required").add("pattern");
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
        log.debug("Tool: grep START pattern={}", params.pattern());
        if (abortFlag.get()) {
            log.debug("Tool: grep ABORTED");
            throw new RuntimeException("Operation aborted");
        }

        var searchPath = params.path() != null ? resolve(params.path()) : cwd;
        var flags = Boolean.TRUE.equals(params.caseSensitive()) ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern;
        try {
            pattern = Pattern.compile(params.pattern(), flags);
        } catch (PatternSyntaxException e) {
            throw new RuntimeException("Invalid regex: " + e.getMessage());
        }

        PathMatcher includeMatcher = params.include() != null
            ? FileSystems.getDefault().getPathMatcher("glob:" + params.include())
            : null;
        var maxResults = params.maxResults() != null ? params.maxResults() : 50;
        var results = new ArrayList<String>();

        try {
            try (var stream = Files.walk(searchPath)) {
                var files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isSkipped(p, searchPath))
                    .filter(p -> !isBinary(p))
                    .filter(p -> includeMatcher == null
                        || includeMatcher.matches(p.getFileName())
                        || includeMatcher.matches(searchPath.relativize(p)))
                    .toList();

                for (var file : files) {
                    if (abortFlag.get() || results.size() >= maxResults) {
                        break;
                    }
                    try {
                        var lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            if (results.size() >= maxResults) {
                                break;
                            }
                            var line = lines.get(i);
                            if (pattern.matcher(line).find()) {
                                var relPath = searchPath.relativize(file);
                                results.add(relPath + ":" + (i + 1) + ":" + line);
                            }
                        }
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Search failed: " + e.getMessage());
        }

        var output = results.isEmpty()
            ? "No matches found for pattern: " + params.pattern()
            : String.join("\n", results);

        if (results.size() >= maxResults) {
            output += "\n\n[Results truncated at " + maxResults + " matches.]";
        }

        log.debug("Tool: grep DONE matches={}", results.size());
        return new ToolResult(
            List.of(new TextContent(output)),
            new GrepDetails(results.size(), params.pattern()));
    }

    private boolean isSkipped(Path path, Path root) {
        for (var parent : path) {
            if (SKIP_DIRS.contains(parent.toString())) {
                return true;
            }
        }
        var relative = root.relativize(path).toString().replace(java.io.File.separatorChar, '/');
        return relative.startsWith(".");
    }

    private boolean isBinary(Path path) {
        var name = path.getFileName().toString().toLowerCase();
        for (var ext : BINARY_EXTS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }

    public record Params(String pattern, String include, String path, Boolean caseSensitive, Integer maxResults) {}
    public record GrepDetails(int matchCount, String pattern) {}
}
