package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class FindTool implements AgentTool<FindTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;

    public FindTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.GLOB_FILES;
    }

    @Override
    public String description() {
        return "Find files by glob pattern (e.g., *.java, src/**/*.ts). "
            + "PATH is relative to workspace root. "
            + "Returns JSON: {\"pattern\":\"...\",\"totalResults\":N,\"results\":[\"...\"],\"truncated\":true|false}. "
            + "Skips common build/dependency directories.";
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
        addStr(props, "path", "Subdirectory relative to workspace root (default: workspace root)");
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
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.pattern() == null || params.pattern().isBlank()) {
            return ToolResult.error("Glob pattern is required.");
        }

        Path rootDir = sandboxGuard.getWorkspaceRoot();
        String cleanPattern = params.pattern().replace("\\", "/");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + cleanPattern);

        List<String> matchedFiles = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(rootDir)) {
            List<Path> allPaths = walk.toList();

            for (Path path : allPaths) {
                if (abortFlag != null && abortFlag.get()) {
                    return ToolResult.error("Glob operation aborted.");
                }

                Path relativePath = rootDir.relativize(path);

                if (matcher.matches(relativePath)) {
                    try {
                        sandboxGuard.validateAndResolve(path.toString());
                        matchedFiles.add(relativePath.toString().replace("\\", "/"));
                    } catch (Exception e) {
                        // Ignoriere unbefugte Pfade
                    }
                }
            }
        } catch (Exception e) {
            return ToolResult.error("Error during file system traversal: " + e.getMessage());
        }

        if (matchedFiles.isEmpty()) {
            return ToolResult.success("No files matched the pattern: " + params.pattern());
        }

        return ToolResult.success("Found matching files:\n" + String.join("\n", matchedFiles));
    }

    public record Params(String pattern) {}
}