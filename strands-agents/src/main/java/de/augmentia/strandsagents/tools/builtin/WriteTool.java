package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.core.internal.WorkspacePaths;
import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.TextContent;
import de.augmentia.strandsagents.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteTool implements AgentTool<WriteTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(WriteTool.class);
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final WorkspacePaths workspacePaths;

    public WriteTool(Path cwd) {
        try {
            this.workspacePaths = new WorkspacePaths(cwd);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String description() {
        return "Write content to a file. Creates parent directories automatically.";
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
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: write START path={}", params.path());
        if (abortFlag.get()) {
            log.debug("Tool: write ABORTED");
            throw new RuntimeException("Operation aborted");
        }
        var path = workspacePaths.resolve(params.path());
        try {
            var parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, params.content());
            log.debug("Tool: write DONE bytes={}", params.content().length());
            return new ToolResult(
                List.of(new TextContent("Successfully wrote " + params.content().length() + " bytes to " + params.path())),
                null);
        } catch (IOException e) {
            log.debug("Tool: write ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record Params(String path, String content) {}
}
