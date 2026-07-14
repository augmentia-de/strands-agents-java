package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.JsonContent;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Writes or overwrites a single file with the specified content securely inside the sandboxed workspace.
 */
public class WriteTool implements AgentTool<WriteTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;

    public WriteTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.WRITE_FILE;
    }

    @Override
    public String description() {
        return "Writes or overwrites a single file with the specified content securely inside the sandbox.";
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
        addStr(props, "filePath", "filePath to the file relative to workspace root");
        addStr(props, "content", "Content to write to the file");
        schema.putArray("required").add("filePath").add("content");
        return schema;
    }

    private void addStr(ObjectNode p, String n, String d) {
        var node = p.putObject(n);
        node.put("type", "string");
        node.put("description", d);
    }

    private static String trunc(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * Writes content to a file after validating the path against the sandbox guard.
     *
     * @param toolCallId unique identifier for this tool invocation
     * @param params     the write parameters (filePath, content)
     * @param abortFlag  flag to signal premature cancellation
     * @param onUpdate   callback for streaming intermediate results
     * @return the tool result indicating success or failure
     */
    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.filePath() == null || params.filePath().isBlank()) {
            return ToolResult.error("filePath is required.");
        }
        if (params.content() == null) {
            return ToolResult.error("content is required.");
        }

        try {
            Path securePath = sandboxGuard.validateAndResolve(AgentTool.relativePath(params.filePath()));

            if (securePath.getParent() != null) {
                Files.createDirectories(securePath.getParent());
            }

            var bytes = Files.writeString(securePath, params.content());
            var json = SCHEMA_MAPPER.createObjectNode();
            json.put("filePath", params.filePath());
            json.put("bytes", params.content().length());
            return ToolResult.mixed("Successfully wrote " + params.content().length() + " bytes to: " + params.filePath(), json);

        } catch (Exception e) {
            return ToolResult.error("Failed to write file due to security or I/O error: " + e.getMessage());
        }
    }

    /**
     * Parameters for writing a file: the target file path and the content to write.
     */
    public record Params(String filePath, String content) {}
}