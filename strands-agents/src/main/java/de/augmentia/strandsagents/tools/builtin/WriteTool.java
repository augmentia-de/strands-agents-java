package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
        addStr(props, "path", "Path to the file relative to workspace root");
        addStr(props, "content", "Content to write to the file");
        schema.putArray("required").add("path").add("content");
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

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.filePath() == null || params.filePath().isBlank()) {
            return ToolResult.error("filePath is required.");
        }
        if (params.content() == null) {
            return ToolResult.error("content is required.");
        }

        try {
            Path securePath = sandboxGuard.validateAndResolve(params.filePath());

            if (securePath.getParent() != null) {
                Files.createDirectories(securePath.getParent());
            }

            Files.writeString(securePath, params.content());
            return ToolResult.success("Successfully wrote file to: " + securePath.getFileName());

        } catch (Exception e) {
            return ToolResult.error("Failed to write file due to security or I/O error: " + e.getMessage());
        }
    }

    public record Params(String filePath, String content) {}
}