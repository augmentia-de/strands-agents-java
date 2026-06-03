package de.augmentia.strandsagents.core.tools.local;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.augmentia.strandsagents.core.tools.AgentTool;
import de.augmentia.strandsagents.core.tools.TextContent;
import de.augmentia.strandsagents.core.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BashTool implements AgentTool<BashTool.Params> {
    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final int MAX_LINES = 300;
    private static final int MAX_BYTES = 30_720;
    private final Path cwd;

    public BashTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a bash command. Output truncated to last " + MAX_LINES + " lines. "
            + "Note: this tool is NOT restricted to the workspace directory. "
            + "The command runs in the workspace directory but can access any path the process has permissions for.";
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
        addStr(props, "command", "Bash command to execute");
        addInt(props, "timeout", "Timeout in seconds (optional)");
        schema.putArray("required").add("command");
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

    private static String trunc(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "..." : s;
    }

    @Override
    public ToolResult execute(String toolCallId, Params params, AtomicBoolean abortFlag, Consumer<ToolResult> onUpdate) {
        log.debug("Tool: bash START command={}", trunc(params.command(), 500));
        try {
            var shell = System.getenv().getOrDefault("SHELL", "/bin/bash");
            var pb = new ProcessBuilder(shell, "-c", params.command());
            pb.directory(cwd.toFile());
            pb.environment().putAll(System.getenv());
            var process = pb.start();
            if (params.timeout() != null && params.timeout() > 0) {
                var timer = new java.util.Timer();
                timer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        process.destroyForcibly();
                    }
                }, params.timeout() * 1000L);
            }
            var lines = new ArrayList<String>();
            var totalBytes = new long[]{0};
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                var line = reader.readLine();
                while (line != null && !abortFlag.get()) {
                    lines.add(line);
                    totalBytes[0] += line.length() + 1;
                    while (lines.size() > MAX_LINES || totalBytes[0] > MAX_BYTES * 2) {
                        totalBytes[0] -= lines.removeFirst().length() + 1;
                    }
                    if (onUpdate != null) {
                        onUpdate.accept(new ToolResult(List.of(new TextContent(String.join("\n", lines))), null));
                    }
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
                log.debug("Tool: bash ERROR exitCode={}", exitCode);
                throw new RuntimeException(result);
            }
            log.debug("Tool: bash DONE exitCode={} lines={}", exitCode, lines.size());
            return new ToolResult(List.of(new TextContent(trunc(result, 500))), null);
        } catch (IOException | InterruptedException e) {
            if (abortFlag.get()) {
                log.debug("Tool: bash ABORTED");
                throw new RuntimeException("Command aborted", e);
            }
            log.debug("Tool: bash ERROR: {}", e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public record Params(String command, Integer timeout) {}
}
