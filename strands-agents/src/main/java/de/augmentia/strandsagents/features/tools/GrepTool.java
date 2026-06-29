package de.augmentia.strandsagents.features.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.*;

import de.augmentia.strandsagents.features.internal.WorkspacePaths;
import de.augmentia.strandsagents.features.tools.AgentTool;
import de.augmentia.strandsagents.features.tools.TextContent;
import de.augmentia.strandsagents.features.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrepTool implements AgentTool<GrepTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(GrepTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LINE_CHARS = 500;

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

    private final WorkspacePaths workspacePaths;

    public GrepTool(Path cwd) {
        try {
            this.workspacePaths = new WorkspacePaths(cwd);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents for a regex pattern. Skips common build directories and binary files. "
            + "Returns JSON: {\"pattern\":\"...\",\"totalMatches\":N,\"matches\":[{\"file\":\"...\",\"line\":N,\"match\":\"...\",\"truncated\":true|false}],\"truncated\":true|false}.";
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
        addStr(props, "path", "File or directory relative to workspace root (default: workspace root)");
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

        var searchPath = resolveSearchPath(params);
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
        var matches = new ArrayList<Map<String, Object>>();

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (abortFlag.get() || matches.size() >= maxResults) {
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
                    if (abortFlag.get() || matches.size() >= maxResults) {
                        return FileVisitResult.TERMINATE;
                    }
                    var fileName = file.getFileName().toString();
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
                            if (matches.size() >= maxResults) return;
                            lineNum[0]++;
                            if (pattern.matcher(line).find()) {
                                var truncated = line.length() > MAX_LINE_CHARS;
                                var matchText = truncated
                                    ? line.substring(0, MAX_LINE_CHARS) + "..."
                                    : line;
                                var relPath = searchPath.relativize(file);
                                var m = new HashMap<String, Object>();
                                m.put("file", relPath.toString());
                                m.put("line", lineNum[0]);
                                m.put("match", matchText);
                                m.put("truncated", truncated);
                                matches.add(m);
                            }
                        });
                    } catch (IOException ignored) {
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

        var root = MAPPER.createObjectNode();
        root.put("pattern", params.pattern());
        var arr = root.putArray("matches");
        for (var m : matches) {
            var obj = arr.addObject();
            obj.put("file", (String) m.get("file"));
            obj.put("line", (int) m.get("line"));
            obj.put("match", (String) m.get("match"));
            obj.put("truncated", (boolean) m.get("truncated"));
        }
        root.put("totalMatches", matches.size());
        boolean truncated = matches.size() >= maxResults;
        root.put("truncated", truncated);

        log.debug("Tool: grep DONE matches={}", matches.size());
        return new ToolResult(
            List.of(new TextContent(root.toString())),
            new GrepDetails(matches.size(), params.pattern()));
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

    private Path resolveSearchPath(Params params) {
        if (params.path() == null) return workspacePaths.workspace();
        try {
            var resolved = workspacePaths.resolve(params.path());
            if (Files.exists(resolved)) return resolved;
            var wsName = workspacePaths.workspace().getFileName().toString();
            if (params.path().equals(wsName)) {
                log.debug("path '{}' resolved to non-existent '{}', using workspace root", params.path(), resolved);
                return workspacePaths.workspace();
            }
            log.debug("path '{}' resolved to non-existent '{}', searching anyway", params.path(), resolved);
            return resolved;
        } catch (RuntimeException e) {
            log.warn("path '{}' resolution failed: {}, using workspace root", params.path(), e.getMessage());
            return workspacePaths.workspace();
        }
    }

    public record Params(String pattern, String include, String path, Boolean caseSensitive, Integer maxResults) {}
    public record GrepDetails(int matchCount, String pattern) {}
}
