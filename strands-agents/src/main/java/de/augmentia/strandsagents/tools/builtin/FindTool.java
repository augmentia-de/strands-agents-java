package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.JsonContent;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Finds files by glob pattern (e.g., *.java, src/**&#47;*.ts) within the sandboxed workspace.
 */
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
        var schema = SCHEMA_MAPPER.createObjectNode();
        schema.put("type", "object");
        var props = schema.putObject("properties");
        addStr(props, "pattern", "Glob pattern (e.g., *.java, src/**/*.ts)");
        addStr(props, "path", "Subdirectory relative to workspace root (default: workspace root)");
        addInt(props, "maxResults", "Maximum number of results (default: 100)");
        schema.putArray("required").add("pattern");
        return schema;
    }

    private static void addStr(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "string");
        node.put("description", d);
    }

    private static void addInt(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "integer");
        node.put("description", d);
    }

    /**
     * Walks the file system from the search directory and returns paths matching the glob pattern.
     *
     * @param toolCallId unique identifier for this tool invocation
     * @param params     the find parameters (pattern, path, maxResults)
     * @param abortFlag  flag to signal premature cancellation
     * @param onUpdate   callback for streaming intermediate results
     * @return the tool result containing matched file paths or an error
     */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.pattern() == null || params.pattern().isBlank()) {
            return ToolResult.error("Glob pattern is required.");
        }

        Path searchDir;
        if (params.path() != null && !params.path().isBlank()) {
            searchDir = sandboxGuard.validateAndResolve(AgentTool.relativePath(params.path()));
        } else {
            searchDir = sandboxGuard.validateAndResolve(".");
        }
        String cleanPattern = params.pattern().replace("\\", "/");
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + cleanPattern);

        List<String> matchedFiles = new ArrayList<>();
        int maxResults = params.maxResults() != null ? params.maxResults() : 100;

        try (Stream<Path> walk = Files.walk(searchDir)) {
            List<Path> allPaths = walk.toList();

            for (Path path : allPaths) {
                if (abortFlag != null && abortFlag.get()) {
                    return ToolResult.error("Glob operation aborted.");
                }

                Path relativePath = sandboxGuard.getWorkspaceRoot().relativize(path);

                if (matcher.matches(relativePath)) {
                    try {
                        sandboxGuard.validateAndResolve(path.toString());
                        matchedFiles.add(relativePath.toString().replace("\\", "/"));
                        if (matchedFiles.size() >= maxResults) {
                            break;
                        }
                    } catch (Exception e) {
                        // Ignoriere unbefugte Pfade
                    }
                }
            }
        } catch (Exception e) {
            return ToolResult.error("Error during file system traversal: " + e.getMessage());
        }

        var json = SCHEMA_MAPPER.createObjectNode();
        json.put("pattern", cleanPattern);
        json.put("total", matchedFiles.size());
        var filesArr = json.putArray("files");
        var truncated = matchedFiles.size() >= maxResults;
        json.put("truncated", truncated);

        if (matchedFiles.isEmpty()) {
            return ToolResult.mixed("No files matched the pattern: " + params.pattern(), json);
        }

        matchedFiles.forEach(filesArr::add);
        var text = "Found matching files:\n" + String.join("\n", matchedFiles);
        if (truncated) {
            text += "\n\n[Truncated: Maximum results reached]";
        }
        return ToolResult.mixed(text, json);
    }

    /**
     * Parameters for file search: a glob pattern, optional subdirectory, and max results limit.
     */
    public record Params(String pattern, String path, Integer maxResults) {}
}