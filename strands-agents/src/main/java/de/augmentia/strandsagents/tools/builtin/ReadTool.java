package de.augmentia.strandsagents.tools.builtin;

import com.fasterxml.jackson.databind.node.ObjectNode;
import de.augmentia.strandsagents.tools.*;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;


public class ReadTool implements AgentTool<ReadTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;
    private final FileReaderFactory readerFactory;

    // Konstruktor-basierte Konfiguration
    public ReadTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
        this.readerFactory = FileReaderFactory.withDefaults();
    }

    public ReadTool(Path cwd, FileReaderFactory readerFactory) {
        try {
            this.sandboxGuard = new FileSandboxGuard(cwd.toString());
            this.readerFactory = readerFactory;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workspace path: " + cwd, e);
        }
    }


    @Override
    public String name() {
        return BaseToolNames.READ_FILES;
    }

    @Override
    public String description() {
        return "Reads the contents of one or multiple files or directories securely inside the sandbox.";
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
        addInt(props, "offset", "Line number to start reading from (1-indexed)");
        addInt(props, "line_start", "Line number to start reading from (1-indexed, alias for offset)");
        addInt(props, "limit", "Maximum number of lines to read");
        addInt(props, "line_end", "Line number to end reading at (inclusive)");
        schema.putArray("required").add("path");
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
        if (params.filePaths() == null || params.filePaths().isEmpty()) {
            return ToolResult.error("No file paths provided.");
        }

        List<String> allBlocks = new ArrayList<>();
        for (String pathStr : params.filePaths()) {
            if (abortFlag != null && abortFlag.get()) {
                return ToolResult.error("Operation aborted.");
            }

            try {
                // ABSICHERUNG
                Path securePath = sandboxGuard.validateAndResolve(pathStr);

                FileReader reader = readerFactory.findReader(securePath);
                if (reader == null) {
                    allBlocks.add("Unsupported file type or directory format for: " + pathStr);
                    continue;
                }

                ToolResult fileResult = reader.read(securePath, params);
                allBlocks.addAll(fileResult.content().stream().map(Object::toString).toList());
            } catch (Exception e) {
                allBlocks.add("Security or I/O Error reading " + pathStr + ": " + e.getMessage());
            }
        }
        return new ToolResult(Arrays.asList(allBlocks.toArray()), null);
    }

    public record Params(List<String> filePaths, Integer offset, Integer limit, Integer line_start, Integer line_end) {}
}