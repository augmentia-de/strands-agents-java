package com.strands.agents.core.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.difflib.DiffUtils;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EditTool implements AgentTool<EditTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(EditTool.class);
    private final Path cwd;

    public EditTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public String description() {
        return "Edit a file by replacing exact text. The oldText must match exactly.";
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
        addStr(props, "path", "Path to the file to edit");
        addStr(props, "oldText", "Exact text to find and replace");
        addStr(props, "newText", "Replacement text");
        schema.putArray("required").add("path").add("oldText").add("newText");
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
        log.debug("Tool: edit START path={} oldText={}", params.path(), trunc(params.oldText(), 200));
        if (abortFlag.get()) {
            log.debug("Tool: edit ABORTED");
            throw new RuntimeException("Operation aborted");
        }
        var path = resolve(params.path());
        try {
            var content = Files.readString(path);
            if (!content.contains(params.oldText())) {
                throw new IOException("Text not found in " + params.path());
            }
            var occurrences = countOccurrences(content, params.oldText());
            if (occurrences > 1) {
                throw new IOException("Text occurs " + occurrences + " times. Must be unique.");
            }
            var newContent = content.replace(params.oldText(), params.newText());
            Files.writeString(path, newContent);

            var origLines = List.of(content.split("\n", -1));
            var newLines = List.of(newContent.split("\n", -1));
            var patch = DiffUtils.diff(origLines, newLines);
            var diff = new StringBuilder();
            for (var delta : patch.getDeltas()) {
                diff.append("--- original\n+++ modified\n");
                for (var l : delta.getSource().getLines()) {
                    diff.append("-").append(l).append("\n");
                }
                for (var l : delta.getTarget().getLines()) {
                    diff.append("+").append(l).append("\n");
                }
            }

            log.debug("Tool: edit DONE path={}", params.path());
            return new ToolResult(
                List.of(new TextContent("Successfully replaced text in " + params.path())),
                new EditDetails(diff.toString()));
        } catch (IOException e) {
            log.debug("Tool: edit ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private int countOccurrences(String content, String text) {
        int c = 0, i = 0;
        while ((i = content.indexOf(text, i)) >= 0) {
            c++;
            i += text.length();
        }
        return c;
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }

    public record Params(String path, String oldText, String newText) {}
    public record EditDetails(String diff) {}
}
