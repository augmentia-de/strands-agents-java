package de.augmentia.strandsagents.mcp.server.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpBashTool {
    private static final Logger log = LoggerFactory.getLogger(McpBashTool.class);
    private static final int MAX_LINES = 300;
    private final Path cwd;

    public McpBashTool(Path cwd) {
        this.cwd = cwd;
    }

    @Tool("Execute a bash command. Output truncated to last " + MAX_LINES + " lines.")
    public String bash(
            @P("Bash command to execute") String command,
            @P("Timeout in seconds (optional)") Integer timeout) {
        log.debug("bash START command={}", command);
        try {
            var shell = System.getenv().getOrDefault("SHELL", "/bin/bash");
            var pb = new ProcessBuilder(shell, "-c", command);
            pb.directory(cwd.toFile());
            pb.environment().putAll(System.getenv());
            var process = pb.start();
            if (timeout != null && timeout > 0) {
                var timer = new java.util.Timer();
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        process.destroyForcibly();
                    }
                }, timeout * 1000L);
            }
            var lines = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                var line = reader.readLine();
                while (line != null) {
                    lines.add(line);
                    line = reader.readLine();
                }
            }
            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                var line = reader.readLine();
                while (line != null) {
                    lines.add(line);
                    line = reader.readLine();
                }
            }
            var exitCode = process.waitFor();
            var result = String.join("\n", lines);
            if (exitCode != 0) {
                result += "\n\nCommand exited with code " + exitCode;
                throw new RuntimeException(result);
            }
            log.debug("bash DONE exitCode={} lines={}", exitCode, lines.size());
            return result;
        } catch (Exception e) {
            log.debug("bash ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
