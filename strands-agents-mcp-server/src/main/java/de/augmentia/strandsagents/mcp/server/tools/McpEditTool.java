package de.augmentia.strandsagents.mcp.server.tools;

import com.github.difflib.DiffUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpEditTool {
    private static final Logger log = LoggerFactory.getLogger(McpEditTool.class);
    private final Path cwd;

    public McpEditTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Edit a file by replacing exact text. The oldText must match exactly and occur only once.")
    public String edit(
            @P("Path to the file to edit") String path,
            @P("Exact text to find and replace") String oldText,
            @P("Replacement text") String newText) {
        log.debug("edit START path={}", path);
        var resolved = resolve(path);
        try {
            var content = Files.readString(resolved);
            if (!content.contains(oldText)) {
                throw new IOException("Text not found in " + path);
            }
            var occurrences = countOccurrences(content, oldText);
            if (occurrences > 1) {
                throw new IOException("Text occurs " + occurrences + " times. Must be unique.");
            }
            var newContent = content.replace(oldText, newText);
            Files.writeString(resolved, newContent);

            var origLines = List.of(content.split("\n", -1));
            var newLines = List.of(newContent.split("\n", -1));
            var patch = DiffUtils.diff(origLines, newLines);
            var diff = new StringBuilder();
            for (var delta : patch.getDeltas()) {
                diff.append("--- original\n+++ modified\n");
                for (var l : delta.getSource().getLines()) diff.append("-").append(l).append("\n");
                for (var l : delta.getTarget().getLines()) diff.append("+").append(l).append("\n");
            }

            log.debug("edit DONE path={}", path);
            return "Successfully replaced text in " + path + "\n\nDiff:\n" + diff;
        } catch (IOException e) {
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
}
