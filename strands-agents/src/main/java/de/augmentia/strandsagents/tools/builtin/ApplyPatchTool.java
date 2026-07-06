package de.augmentia.strandsagents.tools.builtin;

import de.augmentia.strandsagents.tools.AgentTool;
import de.augmentia.strandsagents.tools.ToolResult;
import de.augmentia.strandsagents.tools.security.FileSandboxGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ApplyPatchTool implements AgentTool<ApplyPatchTool.Params> {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private final FileSandboxGuard sandboxGuard;

    public ApplyPatchTool(Path workDir) {
        this.sandboxGuard = new FileSandboxGuard(workDir.toString());
    }

    @Override
    public String name() {
        return BaseToolNames.APPLY_PATCH;
    }

    @Override
    public String description() {
        return "Applies a file patch block containing operations like Add, Update, Move, or Delete securely within the sandbox.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public JsonNode parameterSchema() {
        var factory = SCHEMA_MAPPER.getNodeFactory();

        // Root-Objekt des Schemas erstellen
        var schema = factory.objectNode();
        schema.put("type", "object");

        // Properties-Objekt für die Parameter-Definitionen
        var properties = factory.objectNode();

        // Parameter: patchText
        var patchTextProp = factory.objectNode();
        patchTextProp.put("type", "string");
        patchTextProp.put("description", "The cleartext high-level patch block using *** Begin Patch, *** Add File, *** Update File, *** Move to, *** Delete File syntax.");
        properties.set("patchText", patchTextProp);

        schema.set("properties", properties);

        // Definition der Pflichtfelder (Required Parameters)
        var requiredArray = factory.arrayNode();
        requiredArray.add("patchText");
        schema.set("required", requiredArray);

        return schema;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) throws Exception {
        String patchText = params.patchText();
        if (patchText == null || patchText.isBlank()) {
            return ToolResult.error("patchText is required.");
        }

        String[] lines = patchText.split("\\r?\\n");
        Path currentFile = null;
        StringBuilder currentContent = new StringBuilder();
        String currentMode = ""; 

        StringBuilder log = new StringBuilder("Patch Results:\n");

        try {
            for (String line : lines) {
                if (line.startsWith("*** Begin Patch") || line.startsWith("*** End Patch")) {
                    continue;
                }

                if (line.startsWith("*** Add File:")) {
                    executePending(currentFile, currentContent, currentMode, log);
                    // ABSICHERUNG
                    currentFile = sandboxGuard.validateAndResolve(line.substring(13).trim());
                    currentContent = new StringBuilder();
                    currentMode = "ADD";
                } else if (line.startsWith("*** Update File:")) {
                    executePending(currentFile, currentContent, currentMode, log);
                    // ABSICHERUNG
                    currentFile = sandboxGuard.validateAndResolve(line.substring(16).trim());
                    currentContent = new StringBuilder();
                    currentMode = "UPDATE";
                    if (Files.exists(currentFile)) {
                        currentContent.append(Files.readString(currentFile));
                    }
                } else if (line.startsWith("*** Delete File:")) {
                    executePending(currentFile, currentContent, currentMode, log);
                    // ABSICHERUNG
                    Path delFile = sandboxGuard.validateAndResolve(line.substring(16).trim());
                    if (Files.deleteIfExists(delFile)) {
                        log.append("Deleted file: ").append(delFile.getFileName()).append("\n");
                    }
                    currentFile = null;
                    currentMode = "";
                } else if (line.startsWith("*** Move to:")) {
                    if ("UPDATE".equals(currentMode) && currentFile != null) {
                        // ABSICHERUNG des Zielpfades
                        Path moveTarget = sandboxGuard.validateAndResolve(line.substring(12).trim());
                        executePending(currentFile, currentContent, currentMode, log);
                        
                        if (Files.exists(currentFile)) {
                            Files.createDirectories(moveTarget.getParent());
                            Files.move(currentFile, moveTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            log.append("Moved file to: ").append(moveTarget.getFileName()).append("\n");
                        }
                        currentFile = moveTarget;
                        currentMode = "UPDATE";
                        currentContent = new StringBuilder(Files.readString(currentFile));
                    }
                } else {
                    if ("ADD".equals(currentMode) && line.startsWith("+")) {
                        currentContent.append(line.substring(1)).append("\n");
                    } else if ("UPDATE".equals(currentMode)) {
                        if (line.startsWith("+")) {
                            currentContent.append(line.substring(1)).append("\n");
                        } else if (line.startsWith("-")) {
                            String clean = line.substring(1);
                            int idx = currentContent.indexOf(clean);
                            if (idx != -1) {
                                currentContent.delete(idx, idx + clean.length());
                            }
                        }
                    }
                }
            }
            executePending(currentFile, currentContent, currentMode, log);
        } catch (Exception e) {
            return ToolResult.error("Patch halted due to error / security violation: " + e.getMessage());
        }

        return ToolResult.success(log.toString());
    }

    private void executePending(Path file, StringBuilder content, String mode, StringBuilder log) throws Exception {
        if (file == null || mode.isBlank()) return;
        if ("ADD".equals(mode) || "UPDATE".equals(mode)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content.toString());
            log.append("Saved (").append(mode).append("): ").append(file.getFileName()).append("\n");
        }
    }

    public record Params(String patchText) {}
}