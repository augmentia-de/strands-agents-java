package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpWriteTool {
    private static final Logger log = LoggerFactory.getLogger(McpWriteTool.class);
    private final Path cwd;

    public McpWriteTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Write content to a file. Creates parent directories automatically.")
    public String write(
            @P("Path to the file to write (relative or absolute)") String path,
            @P("Content to write to the file") String content) {
        log.debug("write START path={}", path);
        var resolved = resolve(path);
        try {
            var parent = resolved.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(resolved, content);
            log.debug("write DONE bytes={}", content.length());
            return "Successfully wrote " + content.length() + " bytes to " + path;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Path resolve(String path) {
        var p = Paths.get(path);
        return p.isAbsolute() ? p : cwd.resolve(p).normalize();
    }
}
