package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GrepTool implements AgentTool<GrepTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;

    public GrepTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.GREP_SEARCH;
    }

    @Override
    public String description() {
        return "Searches for a regular expression pattern or substring inside all files within a sub-directory or the entire sandbox.";
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
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.pattern() == null || params.pattern().isBlank()) {
            return ToolResult.error("Search pattern is required.");
        }

        String subDir = (params.directory() != null && !params.directory().isBlank()) ? params.directory() : ".";

        Path secureStartDir;
        try {
            secureStartDir = sandboxGuard.validateAndResolve(subDir);
        } catch (Exception e) {
            return ToolResult.error("Security violation on directory: " + e.getMessage());
        }

        Pattern regexPattern = params.isRegex() != null && params.isRegex()
                ? Pattern.compile(params.pattern())
                : Pattern.compile(Pattern.quote(params.pattern()));

        StringBuilder searchResults = new StringBuilder();
        int matchCount = 0;
        int maxMatches = params.maxResults() != null ? params.maxResults() : 250;

        try (Stream<Path> walk = Files.walk(secureStartDir)) {
            List<Path> filesToProcess = walk.filter(Files::isRegularFile).toList();

            for (Path file : filesToProcess) {
                if (abortFlag != null && abortFlag.get()) {
                    return ToolResult.error("Grep search aborted.");
                }
                if (matchCount >= maxMatches) {
                    searchResults.append("\n[Truncated: Maximum results reached]");
                    break;
                }

                try {
                    sandboxGuard.validateAndResolve(file.toString());

                    String contentType = Files.probeContentType(file);
                    if (contentType != null && (contentType.contains("image") || contentType.contains("video") || contentType.contains("zip"))) {
                        continue;
                    }

                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (regexPattern.matcher(line).find()) {
                            Path relativePath = sandboxGuard.getWorkspaceRoot().relativize(file);
                            searchResults.append(relativePath).append(":").append(i + 1).append(": ").append(line.trim()).append("\n");
                            matchCount++;
                            if (matchCount >= maxMatches) break;
                        }
                    }
                } catch (Exception e) {
                    // Ignoriere unlesbare/Sonderdateien während des Greps
                }
            }
        }

        if (matchCount == 0) {
            return ToolResult.success("No matches found for pattern: " + params.pattern());
        }

        return ToolResult.success(searchResults.toString());
    }

    public record Params(String pattern, String directory, Boolean isRegex, Integer maxResults) {}
}