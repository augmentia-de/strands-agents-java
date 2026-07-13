package de.augmentia.strandsagents.tools.builtin;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MultiEditTool implements AgentTool<MultiEditTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;

    public MultiEditTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.MULTI_EDIT;
    }

    @Override
    public String description() {
        return "Performs multiple exact string replacements across one or multiple files atomically inside the sandbox.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public JsonNode parameterSchema() {
        var factory = SCHEMA_MAPPER.getNodeFactory();
        var schema = factory.objectNode();
        schema.put("type", "object");

        var properties = factory.objectNode();

        // Das Array "edits"
        var editsProp = factory.objectNode();
        editsProp.put("type", "array");
        editsProp.put("description", "List of individual file edits to perform atomically.");

        // Definition des Objekttyps innerhalb des Arrays (FileEdit)
        var itemSchema = factory.objectNode();
        itemSchema.put("type", "object");

        var itemProperties = factory.objectNode();

        var filePathProp = factory.objectNode();
        filePathProp.put("type", "string");
        filePathProp.put("description", "The path of the file to edit.");
        itemProperties.set("filePath", filePathProp);

        var oldStringProp = factory.objectNode();
        oldStringProp.put("type", "string");
        oldStringProp.put("description", "The exact substring block to search for and replace.");
        itemProperties.set("oldString", oldStringProp);

        var newStringProp = factory.objectNode();
        newStringProp.put("type", "string");
        newStringProp.put("description", "The new string block to insert.");
        itemProperties.set("newString", newStringProp);

        var replaceAllProp = factory.objectNode();
        replaceAllProp.put("type", "boolean");
        replaceAllProp.put("description", "If true, replaces all occurrences. If false or omitted, ensures exactly one occurrence exists.");
        itemProperties.set("replaceAll", replaceAllProp);

        itemSchema.set("properties", itemProperties);

        // Pflichtfelder innerhalb des Objekts im Array
        var itemRequired = factory.arrayNode();
        itemRequired.add("filePath");
        itemRequired.add("oldString");
        itemRequired.add("newString");
        itemSchema.set("required", itemRequired);

        editsProp.set("items", itemSchema);
        properties.set("edits", editsProp);

        schema.set("properties", properties);

        var requiredArray = factory.arrayNode();
        requiredArray.add("edits");
        schema.set("required", requiredArray);

        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        if (params.edits() == null || params.edits().isEmpty()) {
            return ToolResult.error("No edits provided.");
        }

        StringBuilder logSummary = new StringBuilder("Execution summary:\n");

        for (FileEdit edit : params.edits()) {
            if (abortFlag != null && abortFlag.get()) {
                return ToolResult.error("Transaction aborted midway.");
            }

            try {
                // ABSICHERUNG
                Path securePath = sandboxGuard.validateAndResolve(AgentTool.relativePath(edit.filePath()));

                if (!Files.exists(securePath)) {
                    return ToolResult.error("File does not exist: " + edit.filePath());
                }

                String content = Files.readString(securePath);
                if (!content.contains(edit.oldString())) {
                    return ToolResult.error("Original string (oldString) not found in file: " + edit.filePath());
                }

                int firstIdx = content.indexOf(edit.oldString());
                int lastIdx = content.lastIndexOf(edit.oldString());
                if (firstIdx != lastIdx && !Boolean.TRUE.equals(edit.replaceAll())) {
                    return ToolResult.error("Multiple occurrences found in " + edit.filePath() + ". Set replaceAll to true.");
                }

                String newContent = Boolean.TRUE.equals(edit.replaceAll())
                        ? content.replace(edit.oldString(), edit.newString())
                        : content.substring(0, firstIdx) + edit.newString() + content.substring(firstIdx + edit.oldString().length());

                Files.writeString(securePath, newContent);
                logSummary.append("Successfully modified: ").append(edit.filePath()).append("\n");

            } catch (Exception e) {
                return ToolResult.error("Failed to edit " + edit.filePath() + " due to safety/I/O error: " + e.getMessage());
            }
        }

        return ToolResult.success(logSummary.toString());
    }

    public record Params(List<FileEdit> edits) {}
    public record FileEdit(String filePath, String oldString, String newString, Boolean replaceAll) {}
}